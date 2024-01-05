package com.example.handler;

import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.Proxy;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.imageio.IIOException;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.FileImageOutputStream;
import javax.imageio.stream.ImageOutputStream;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation.Builder;
import javax.ws.rs.core.Response;

import org.apache.http.HttpHost;
import org.apache.http.HttpStatus;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.conn.SystemDefaultDnsResolver;
import org.glassfish.jersey.apache.connector.ApacheConnectorProvider;
import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.client.ClientProperties;
import org.glassfish.jersey.client.JerseyClientBuilder;
import org.glassfish.jersey.jetty.connector.JettyHttp2Connector;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.applitools.eyes.TestResults;

public class ApplitoolsTestResultsHandlerJersey {

	@SuppressWarnings("unused")
	private static final String VERSION = "1.3.4";
	protected static final String STEP_RESULT_API_FORMAT = "/api/sessions/batches/%s/%s/?ApiKey=%s&format=json";
	private static final String RESULT_REGEX = "(?<serverURL>^.+)\\/app\\/batches\\/(?<batchId>\\d+)\\/(?<sessionId>\\d+).*$";
	private static final String IMAGE_TMPL = "%s/step %s %s-%s.png";
	private static final int DEFAULT_TIME_BETWEEN_FRAMES = 500;
	private static final String DiffsUrlTemplate = "%s/api/sessions/batches/%s/%s/steps/%s/diff?ApiKey=%s";
	private static final String UPDATE_SESSIONS = "/api/sessions/batches/%s/updates";
	private static final String UPDATE_SESSIONS_BASELINES = "/api/sessions/batches/%s/baselines";
	private static final int RETRY_REQUEST_INTERVAL = 500; // ms
	private static final int LONG_REQUEST_DELAY_MS = 2000; // ms
	private static final int MAX_LONG_REQUEST_DELAY_MS = 10000; // ms
	private static final double LONG_REQUEST_DELAY_MULTIPLICATIVE_INCREASE_FACTOR = 1.5;

	private static final Charset UTF8 = Charset.forName("UTF-8");

	public static enum RequestMethod {
		GET, HEAD, POST, PUT, PATCH, DELETE, OPTIONS, TRACE;
	}

	private static X509TrustManager xtm = new X509TrustManager() {

		@Override
		public void checkClientTrusted(X509Certificate[] arg0, String arg1) {
			return;
		}

		@Override
		public void checkServerTrusted(X509Certificate[] arg0, String arg1) {
			return;
		}

		@Override
		public X509Certificate[] getAcceptedIssuers() {
			return null;
		}
	};

	private static TrustManager tm[] = { xtm };

	private static HostnameVerifier hv = new HostnameVerifier() {
		@Override
		public boolean verify(String hostname, SSLSession session) {
			return true;
		}
	};

	protected String applitoolsRunKey;
	protected String applitoolsViewKey;
	protected String applitoolsWriteKey;
	protected String serverURL;
	protected String batchID;
	protected String sessionID;
	protected String accountID;

	protected HttpHost proxy = null;
	protected Proxy clientProxy = null;
	protected CredentialsProvider credsProvider = null;
	String encodedCredentials = null;

	private TestResults testResults;
	private String[] stepsNames;
	private ResultStatus[] stepsState;
	private JSONObject testData;
	private String prefix = "";

	private String preparePath(String Path) {
		Path += "/" + prefix;
		if (!Path.contains("/" + batchID + "/" + sessionID)) {
			Path = Path + "/" + batchID + "/" + sessionID;
			File folder = new File(Path);
			if (!folder.exists())
				folder.mkdirs();
		}
		return Path;

	}

	private List<BufferedImage> baselineImages;
	private List<BufferedImage> currentImages;
	private List<BufferedImage> diffImages;

	private int counter = 0;

	private ClientConfig clientConfig = null;

	private String getServerIpUrl(String serverUrl) {
		return serverUrl.replace("eyes.applitools.com", "40.81.2.193");
	}

	public ApplitoolsTestResultsHandlerJersey(TestResults testResults, String viewKey, String proxyServer,
			String proxyPort, String proxyUser, String proxyPassword) throws Exception {

		System.setProperty("javax.net.debug", "ssl,handshake");
		System.setProperty("com.sun.security.enableAIAcaIssuers", "true");

		clientConfig = new ClientConfig();

		if ((proxyServer != null) && (proxyPort != null)) {

			clientConfig.property(ClientProperties.PROXY_URI, "http://" + proxyServer + ":" + proxyPort);

			clientProxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyServer, Integer.parseInt(proxyPort)));
			if ((proxyPassword != null) && (proxyUser != null)) {
				clientConfig.property(ClientProperties.PROXY_USERNAME, proxyUser);
				clientConfig.property(ClientProperties.PROXY_PASSWORD, proxyPassword);
				System.setProperty("http.proxyUser", proxyUser);
				System.setProperty("http.proxyPassword", proxyPassword);

			}
		}
		this.applitoolsViewKey = viewKey;
		this.testResults = testResults;
		Pattern pattern = Pattern.compile(RESULT_REGEX);
		Matcher matcher = pattern.matcher(testResults.getUrl());
		if (!matcher.find())
			throw new Exception("Unexpected result URL - Not parsable");
		this.batchID = matcher.group("batchId");
		this.sessionID = matcher.group("sessionId");
		this.serverURL = matcher.group("serverURL");
		// this.serverURL = getServerIpUrl(this.serverURL);
		String accountIdParamName = "accountId=";
		this.accountID = testResults.getUrl()
				.substring(testResults.getUrl().indexOf(accountIdParamName) + accountIdParamName.length());

		String url = String.format(serverURL + STEP_RESULT_API_FORMAT, this.batchID, this.sessionID,
				this.applitoolsViewKey);
		String json = readJsonStringFromUrl(url);
		this.testData = new JSONObject(json);
		this.stepsNames = calculateStepsNames();
		this.stepsState = prepareStepResults();
		this.baselineImages = getBufferedImagesByType("Baseline");
		this.currentImages = getBufferedImagesByType("Current");
		this.diffImages = getBufferedImagesByType("Diff");

	}

	public ApplitoolsTestResultsHandlerJersey(TestResults testResults, String viewKey, String proxyServer,
			String proxyPort) throws Exception {
		this(testResults, viewKey, proxyServer, proxyPort, null, null);
	}

	public ApplitoolsTestResultsHandlerJersey(TestResults testResults, String viewKey) throws Exception {
		this(testResults, viewKey, null, null, null, null);
	}

	public void acceptChanges(List<ResultStatus> desiredStatuses, String writeKey) {
		try {
			if (writeKey != null) {
				this.applitoolsWriteKey = writeKey;
				acceptChangesToSteps(this.stepsState, desiredStatuses);
			} else {
				throw new Error("No Write Key was provided to the function!");
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

	}

	private URL[] getDiffUrls() {
		URL[] urls = new URL[stepsState.length];
		for (int step = 0; step < this.testResults.getSteps(); ++step) {
			if ((stepsState[step] == ResultStatus.UNRESOLVED) || (stepsState[step] == ResultStatus.FAILED)) {
				try {
					urls[step] = new URL(String.format(DiffsUrlTemplate, this.serverURL, this.batchID, this.sessionID,
							step + 1, this.applitoolsViewKey));
				} catch (MalformedURLException e) {
					e.printStackTrace();
				}
			} else
				urls[step] = null;
		}
		return urls;
	}

	public ResultStatus[] calculateStepResults() {
		if (stepsState == null)
			try {
				stepsState = prepareStepResults();
			} catch (Exception e) {
				e.printStackTrace();
			}
		return stepsState;
	}

	public String getLinkToStep(int step) {

		String link = testResults.getUrl().replaceAll("batches", "sessions");
		StringBuffer buf = new StringBuffer(link);
		int index = link.indexOf("?accountId=");
		return (buf.insert(index, "/steps/" + step).toString());
	}

	private ResultStatus[] prepareStepResults() throws Exception {
		JSONArray expected = this.testData.getJSONArray("expectedAppOutput");
		JSONArray actual = this.testData.getJSONArray("actualAppOutput");

		int steps = Math.max(expected.length(), actual.length());
		ResultStatus[] retStepResults = new ResultStatus[steps];

		for (int i = 0; i < steps; i++) {

			if (expected.get(i) == JSONObject.NULL) {
				retStepResults[i] = ResultStatus.NEW;
			} else if (actual.get(i) == JSONObject.NULL) {
				retStepResults[i] = ResultStatus.MISSING;
			} else if (actual.getJSONObject(i).getBoolean("isMatching")) {
				retStepResults[i] = ResultStatus.PASSED;
			} else {
				retStepResults[i] = checkStepIfFailedOrUnresolved(i);
			}
		}
		return retStepResults;
	}

	private void acceptChangesToSteps(ResultStatus[] results, List<ResultStatus> desiredStatuses) throws Exception {
		int sizeResultStatus = results.length;
		for (int i = 0; i < sizeResultStatus; i++) {
			if (desiredStatuses.contains(results[i])) {
				String url = String.format(serverURL + UPDATE_SESSIONS, this.batchID);
				url = url + "?apiKey=" + this.applitoolsWriteKey;

				String payload = String.format(
						"{\"updates\":[{\"id\":\"%s\",\"batchId\":\"%s\",\"stepUpdates\":[{\"index\":%d,\"replaceExpected\":true}]}]}",
						this.sessionID, this.batchID, i);

				postJsonToURL(url, payload);
				url = String.format(serverURL + UPDATE_SESSIONS_BASELINES, this.batchID);
				url = url + "?accountId=" + this.accountID + "&apiKey=" + this.applitoolsWriteKey;
				payload = String.format("{\"ids\":[\"%s\"]}", this.sessionID);
				postJsonToURL(url, payload);
			}
		}

	}

	private ResultStatus checkStepIfFailedOrUnresolved(int i) throws JSONException {

		if (getBugRegionsOfStep(i).length() == 0) {
			return ResultStatus.UNRESOLVED;
		} else {
			JSONArray bugRegions = getBugRegionsOfStep(i);
			for (int j = 1; j < bugRegions.length(); j++) {
				if (!(((JSONObject) (bugRegions.get(j))).getBoolean("isDisabled"))) {
					return ResultStatus.FAILED;
				}
			}
		}
		return ResultStatus.UNRESOLVED;

	}

	private JSONArray getBugRegionsOfStep(int i) throws JSONException {
		JSONArray expected = this.testData.getJSONArray("expectedAppOutput");
		return expected.getJSONObject(i).getJSONObject("annotations").getJSONArray("mismatching");
	}

	public String[] getStepsNames() {
		return this.stepsNames;
	}

	private String[] calculateStepsNames() throws Exception {
		ResultStatus[] stepResults = calculateStepResults();
		JSONArray expected = this.testData.getJSONArray("expectedAppOutput");
		JSONArray actual = this.testData.getJSONArray("actualAppOutput");
		int steps = expected.length();
		String[] StepsNames = new String[steps];

		for (int i = 0; i < steps; i++) {
			if (stepResults[i] != ResultStatus.NEW) {
				StepsNames[i] = expected.getJSONObject(i).optString("tag");
			} else {
				StepsNames[i] = actual.getJSONObject(i).optString("tag");
			}
		}
		return StepsNames;
	}

	private Client getJerseyClient() {
		
		// Client client = ClientBuilder.newClient(clientConfig);

//		clientConfig.connectorProvider((jaxrsClient, config1) -> {
//			final JettyHttp2Connector jettyHttp2Connector = new JettyHttp2Connector(jaxrsClient, config1);
//			jettyHttp2Connector.getHttpClient().setSocketAddressResolver((s, i, promise) -> {
//				List<InetSocketAddress> result = null;
//				try {
//					if (s == "eyes.applitools.com") {
//						System.out.println("Using custom DNS resolver for eyes.applitools.com...");
//						result = Collections
//								.singletonList(new InetSocketAddress(InetAddress.getByName("40.81.2.193"), i));
//					} else {
//						new SystemDefaultDnsResolver();
//						result = Collections
//								.singletonList(new InetSocketAddress(new SystemDefaultDnsResolver().resolve(s)[0], i));
//					}
//					promise.succeeded(result);
//				} catch (UnknownHostException e) {
//					throw new IllegalStateException(e);
//				}
//
//			});
//			return jettyHttp2Connector;
//		});
//
//		return ClientBuilder.newBuilder()
//				.register(new ApacheConnectorProvider())
//				.withConfig(clientConfig).build();
		
		clientConfig.connectorProvider(new ApacheConnectorProvider());
		return new JerseyClientBuilder().withConfig(clientConfig).build();

	}

	private void initHttpsURLConnection() throws Exception {
		SSLContext ctx = SSLContext.getInstance("SSL");
		ctx.init(null, tm, null);
		HttpsURLConnection.setDefaultSSLSocketFactory(ctx.getSocketFactory());
		HttpsURLConnection.setDefaultHostnameVerifier(hv);
	}

	private String readJsonStringFromUrl(String url) throws Exception {

//		initHttpsURLConnection();
		Response response = null;
		response = runLongRequest(url, RequestMethod.GET, null, null);
		InputStream is = response.readEntity(InputStream.class);
		try {
			BufferedReader rd = new BufferedReader(new InputStreamReader(is, UTF8));
			return readAll(rd);
		} finally {
			if (null != is)
				is.close();
			if (null != response)
				response.close();
		}
	}

	private String postJsonToURL(String url, String payload) throws Exception {

//		initHttpsURLConnection();
		Map<String, String> headers = new HashMap<String, String>();
		headers.put("Accept", "application/json");
		headers.put("Content-Type", "application/json");

		Response response = null;
		response = runLongRequest(url, RequestMethod.POST, payload, headers);
		InputStream is = response.readEntity(InputStream.class);
		try {
			BufferedReader rd = new BufferedReader(new InputStreamReader(is, UTF8));
			return readAll(rd);

		} finally {
			if (null != is)
				is.close();
			if (null != response)
				response.close();
		}
	}

	protected String readAll(Reader rd) throws IOException {
		StringBuilder sb = new StringBuilder();
		int cp;
		while ((cp = rd.read()) != -1) {
			sb.append((char) cp);
		}
		return sb.toString();
	}

	private ArrayList<BufferedImage> getBufferedImagesByType(String type)
			throws IOException, JSONException, InterruptedException {
		URL[] urls = null;
		ArrayList<BufferedImage> images = new ArrayList<BufferedImage>();

		if (type == "Baseline")
			urls = getBaselineImagesURLS();
		else if (type == "Current")
			urls = getCurrentImagesURLS();
		else if (type == "Diff")
			urls = getDiffUrls();

		if (urls != null) {
			for (int i = 0; i < urls.length; i++) {
				if (null != urls[i]) {
					Response response = null;
					response = runLongRequest(urls[i].toString(), RequestMethod.GET, null, null);
					InputStream is = response.readEntity(InputStream.class);
					try {
						BufferedImage image = ImageIO.read(is);
						images.add(image);
					} catch (IOException e) {
						e.printStackTrace();
					} finally {
						if (null != is)
							is.close();
						if (null != response)
							response.close();
					}

				} else {
					images.add(null);
				}
			}
		}

		return images;
	}

	public List<BufferedImage> getBaselineBufferedImages() throws JSONException {
		return this.baselineImages;
	}

	public List<BufferedImage> getCurrentBufferedImages() throws JSONException {
		return this.currentImages;
	}

	public List<BufferedImage> getDiffsBufferedImages() throws JSONException {
		return this.diffImages;
	}

	public void downloadDiffs(String path) throws Exception {
		URL[] urls = getDiffUrls();
		if (urls != null) {
			saveImagesInFolder(preparePath(path), "Diff");
		}
	}

	public void downloadBaselineImages(String path) throws IOException, InterruptedException, JSONException {
		saveImagesInFolder(preparePath(path), "Baseline");
	}

	public void downloadCurrentImages(String path) throws IOException, InterruptedException, JSONException {
		saveImagesInFolder(preparePath(path), "Current");
	}

	public void downloadImages(String path) throws Exception {
		downloadBaselineImages(path);
		downloadCurrentImages(path);
	}

	private void saveImagesInFolder(String path, String imageType) {
		List<BufferedImage> imagesList = null;
		ResultStatus[] resultStatus = this.calculateStepResults();

		if (imageType == "Current")
			imagesList = this.currentImages;
		else if (imageType == "Baseline")
			imagesList = this.baselineImages;
		else if (imageType == "Diff")
			imagesList = this.diffImages;

		if (null != imagesList) {
			for (int i = 0; i < imagesList.size(); i++) {
				if (null != imagesList.get(i)) {
					String windowsCompatibleStepName = makeWindowsFileNameCompatible(stepsNames[i]);
					File outputFile = new File(
							String.format(IMAGE_TMPL, path, (i + 1), windowsCompatibleStepName, imageType));
					try {
						ImageIO.write(imagesList.get(i), "png", outputFile);
					} catch (IOException e) {
						e.printStackTrace();
					}
				} else {
					System.out.println("No " + imageType + " image was downloaded at step " + (i + 1)
							+ " as this step status is " + resultStatus[i]);
				}

			}
		}
	}

	// Unused private method.
	/*
	 * private void saveImagesInFolder(String path, String imageType, URL[]
	 * imageURLS) throws InterruptedException, IOException, JSONException { for (int
	 * i = 0; i < imageURLS.length; i++) { if (imageURLS[i] == null) {
	 * System.out.println("No " + imageType + " image in step " + (i + 1) + ": " +
	 * stepsNames[i]); } else { String windowsCompatibleStepName =
	 * makeWindowsFileNameCompatible(stepsNames[i]);
	 * 
	 * CloseableHttpResponse response = null; HttpGet get = new
	 * HttpGet(imageURLS[i].toString()); CloseableHttpClient client =
	 * getCloseableHttpClient();
	 * 
	 * response = runLongRequest(get); InputStream is =
	 * response.getEntity().getContent(); try { BufferedImage bi = ImageIO.read(is);
	 * ImageIO.write(bi, "png", new File(String.format(IMAGE_TMPL, path, (i + 1),
	 * windowsCompatibleStepName, imageType))); } finally { if (null != is)
	 * is.close(); if (null != client) client.close(); if (null != response)
	 * response.close(); }
	 * 
	 * } } }
	 */

	private String makeWindowsFileNameCompatible(String stepName) {
		stepName = stepName.replace('/', '~');
		stepName = stepName.replace("\\", "~");
		stepName = stepName.replace(':', '~');
		stepName = stepName.replace('*', '~');
		stepName = stepName.replace('?', '~');
		stepName = stepName.replace('"', '~');
		stepName = stepName.replace("'", "~");
		stepName = stepName.replace('<', '~');
		stepName = stepName.replace('>', '~');
		stepName = stepName.replace('|', '~');

		while (!stepName.equals(stepName.replace("~~", "~"))) {
			stepName = stepName.replace("~~", "~");
		}
		return stepName;
	}

	private URL[] getDownloadImagesURLSByType(String imageType) throws JSONException {
		String[] imageIds = getImagesUIDs(this.sessionID, this.batchID, imageType);
		URL[] URLS = new URL[calculateStepResults().length];
		for (int i = 0; i < imageIds.length; i++) {
			if (imageIds[i] == null) {
				URLS[i] = null;
			} else
				try {
					URLS[i] = new URL(String.format("%s/api/images/%s?apiKey=%s", this.serverURL, imageIds[i],
							this.applitoolsViewKey));
				} catch (MalformedURLException e) {
					e.printStackTrace();
				}
		}
		return URLS;
	}

	private URL[] getCurrentImagesURLS() throws JSONException {
		return getDownloadImagesURLSByType("Current");
	}

	private URL[] getBaselineImagesURLS() throws JSONException {
		return getDownloadImagesURLSByType("Baseline");
	}

	private String[] getImagesUIDs(String sessionId, String batchId, String imageType) throws JSONException {
		String sessionInfo = null;
		try {
			sessionInfo = getSessionInfo(sessionId, batchId);
		} catch (IOException | InterruptedException e) {
			e.printStackTrace();
		}
		JSONObject obj = new JSONObject(sessionInfo);
		if (imageType == "Baseline") {
			return getImagesUIDs(obj.getJSONArray("expectedAppOutput"));
		} else if (imageType == "Current") {
			return getImagesUIDs(obj.getJSONArray("actualAppOutput"));
		}
		return null;
	}

	private String[] getImagesUIDs(JSONArray infoTable) throws JSONException {
		String[] retUIDs = new String[infoTable.length()];

		for (int i = 0; i < infoTable.length(); i++) {
			if (infoTable.isNull(i)) {
				retUIDs[i] = null;
			} else {
				JSONObject entry = infoTable.getJSONObject(i);
				JSONObject image = entry.getJSONObject("image");
				retUIDs[i] = (image.getString("id"));
			}
		}
		return retUIDs;
	}

	public void downloadAnimatedGif(String path) throws JSONException {
		downloadAnimatedGif(path, DEFAULT_TIME_BETWEEN_FRAMES);
	}

	public void downloadAnimatedGif(String path, int timeBetweenFramesMS) throws JSONException {

		if (testResults.getMismatches() + testResults.getMatches() > 0) // only if the test isn't new and not all of his
																		// steps are missing
		{
			URL[] baselineImagesURLS = getBaselineImagesURLS();
			URL[] currentImagesURL = getCurrentImagesURLS();
			URL[] diffImagesURL = getDiffUrls();

			List<BufferedImage> base = getBaselineBufferedImages(); // get Baseline Images as BufferedImage
			List<BufferedImage> curr = getCurrentBufferedImages(); // get Current Images as BufferedImage
			List<BufferedImage> diff = getDiffsBufferedImages(); // get Diff Images as BufferedImage

			for (int i = 0; i < stepsState.length; i++) {
				if ((stepsState[i] == ResultStatus.UNRESOLVED) || (stepsState[i] == ResultStatus.FAILED)) {
					List<BufferedImage> list = new ArrayList<BufferedImage>();
					try {
						if (currentImagesURL[i] != null)
							list.add(curr.get(i));
						if (baselineImagesURLS[i] != null)
							list.add(base.get(i));
						if (diffImagesURL[i] != null)
							list.add(diff.get(i));
						String tempPath = preparePath(path) + "/" + (i + 1) + " - AnimatedGif.gif";
						createAnimatedGif(list, new File(tempPath), timeBetweenFramesMS);
					} catch (IOException e) {
						e.printStackTrace();
					}
				} else {
					System.out.println("No Animated GIf created for Step " + (i + 1) + " " + stepsNames[i]
							+ " as it is " + stepsState[i]);
				}
			}
		}
	}

	private String getSessionInfo(String sessionId, String batchId) throws IOException, InterruptedException {
		URL url = new URL(String.format("%s/api/sessions/batches/%s/%s?apiKey=%s&format=json", this.serverURL, batchId,
				sessionId, this.applitoolsViewKey));
		Response response = null;
		response = runLongRequest(url.toString(), RequestMethod.GET, null, null);
		InputStream stream = response.readEntity(InputStream.class);

		try {
			BufferedReader rd = new BufferedReader(new InputStreamReader(stream, UTF8));
			return readAll(rd);
		} finally {
			if (null != stream)
				stream.close();
			if (null != response)
				response.close();
		}
	}

	private static class Rectangle {
		int height;
		int width;

		Rectangle(int width, int height) {
			this.width = width;
			this.height = height;
		}

		Rectangle() {
			this(0, 0);
		}

		Rectangle(BufferedImage i) {
			this(i.getWidth(), i.getHeight());
		}

		static Rectangle maxDimensions(Rectangle a, Rectangle b) {
			return new Rectangle(Integer.max(a.width, b.width), Integer.max(a.height, b.height));
		}

		static Rectangle maxDimensionsOf(List<BufferedImage> images) {
			return images.stream().map(Rectangle::new).reduce(new Rectangle(), Rectangle::maxDimensions);
		}
	}

	private static void createAnimatedGif(List<BufferedImage> images, File target, int timeBetweenFramesMS)
			throws IOException {
		ImageOutputStream output = new FileImageOutputStream(target);
		GifSequenceWriter writer = null;

		Rectangle max = Rectangle.maxDimensionsOf(images);

		try {
			for (BufferedImage image : images) {
				BufferedImage normalized = new BufferedImage(max.width, max.height, image.getType());
				normalized.getGraphics().drawImage(image, 0, 0, null);
				if (writer == null)
					writer = new GifSequenceWriter(output, image.getType(), timeBetweenFramesMS, true);
				writer.writeToSequence(normalized);
			}
		} finally {
			writer.close();
			output.close();
		}
	}

	private static class GifSequenceWriter {
		protected ImageWriter gifWriter;
		protected ImageWriteParam imageWriteParam;
		protected IIOMetadata imageMetaData;

		/**
		 * Creates a new GifSequenceWriter
		 *
		 * @param outputStream        the ImageOutputStream to be written to
		 * @param imageType           one of the imageTypes specified in BufferedImage
		 * @param timeBetweenFramesMS the time between frames in miliseconds
		 * @param loopContinuously    wether the gif should loop repeatedly
		 * @throws IIOException if no gif ImageWriters are found
		 * @author Elliot Kroo (elliot[at]kroo[dot]net)
		 */
		public GifSequenceWriter(ImageOutputStream outputStream, int imageType, int timeBetweenFramesMS,
				boolean loopContinuously) throws IIOException, IOException {
			// my method to create a writer
			gifWriter = getWriter();
			imageWriteParam = gifWriter.getDefaultWriteParam();
			ImageTypeSpecifier imageTypeSpecifier = ImageTypeSpecifier.createFromBufferedImageType(imageType);

			imageMetaData = gifWriter.getDefaultImageMetadata(imageTypeSpecifier, imageWriteParam);

			String metaFormatName = imageMetaData.getNativeMetadataFormatName();

			IIOMetadataNode root = (IIOMetadataNode) imageMetaData.getAsTree(metaFormatName);

			IIOMetadataNode graphicsControlExtensionNode = getNode(root, "GraphicControlExtension");

			graphicsControlExtensionNode.setAttribute("disposalMethod", "none");
			graphicsControlExtensionNode.setAttribute("userInputFlag", "FALSE");
			graphicsControlExtensionNode.setAttribute("transparentColorFlag", "FALSE");
			graphicsControlExtensionNode.setAttribute("delayTime", Integer.toString(timeBetweenFramesMS / 10));
			graphicsControlExtensionNode.setAttribute("transparentColorIndex", "0");

			IIOMetadataNode commentsNode = getNode(root, "CommentExtensions");
			commentsNode.setAttribute("CommentExtension", "Created by MAH");

			IIOMetadataNode appEntensionsNode = getNode(root, "ApplicationExtensions");

			IIOMetadataNode child = new IIOMetadataNode("ApplicationExtension");

			child.setAttribute("applicationID", "NETSCAPE");
			child.setAttribute("authenticationCode", "2.0");

			int loop = loopContinuously ? 0 : 1;

			child.setUserObject(new byte[] { 0x1, (byte) (loop & 0xFF), (byte) ((loop >> 8) & 0xFF) });
			appEntensionsNode.appendChild(child);

			imageMetaData.setFromTree(metaFormatName, root);

			gifWriter.setOutput(outputStream);

			gifWriter.prepareWriteSequence(null);
		}

		public void writeToSequence(RenderedImage img) throws IOException {
			gifWriter.writeToSequence(new IIOImage(img, null, imageMetaData), imageWriteParam);
		}

		/**
		 * Close this GifSequenceWriter object. This does not close the underlying
		 * stream, just finishes off the GIF.
		 */
		public void close() throws IOException {
			gifWriter.endWriteSequence();
		}

		/**
		 * Returns the first available GIF ImageWriter using
		 * ImageIO.getImageWritersBySuffix("gif").
		 *
		 * @return a GIF ImageWriter object
		 * @throws IIOException if no GIF image writers are returned
		 */
		private static ImageWriter getWriter() throws IIOException {
			Iterator<ImageWriter> iter = ImageIO.getImageWritersBySuffix("gif");
			if (!iter.hasNext()) {
				throw new IIOException("No GIF Image Writers Exist");
			} else {
				return iter.next();
			}
		}

		/**
		 * Returns an existing child node, or creates and returns a new child node (if
		 * the requested node does not exist).
		 *
		 * @param rootNode the <tt>IIOMetadataNode</tt> to search for the child node.
		 * @param nodeName the name of the child node.
		 * @return the child node, if found or a new node created with the given name.
		 */
		private static IIOMetadataNode getNode(IIOMetadataNode rootNode, String nodeName) {
			int nNodes = rootNode.getLength();
			for (int i = 0; i < nNodes; i++) {
				if (rootNode.item(i).getNodeName().compareToIgnoreCase(nodeName) == 0) {
					return ((IIOMetadataNode) rootNode.item(i));
				}
			}
			IIOMetadataNode node = new IIOMetadataNode(nodeName);
			rootNode.appendChild(node);
			return (node);
		}

		/**
		 * public GifSequenceWriter( BufferedOutputStream outputStream, int imageType,
		 * int timeBetweenFramesMS, boolean loopContinuously) {
		 */

		@SuppressWarnings("unused")
		public static void main(String[] args) throws Exception {
			if (args.length > 1) {
				// grab the output image type from the first image in the sequence
				BufferedImage firstImage = ImageIO.read(new File(args[0]));

				// create a new BufferedOutputStream with the last argument
				ImageOutputStream output = new FileImageOutputStream(new File(args[args.length - 1]));

				// create a gif sequence with the type of the first image, 1 second
				// between frames, which loops continuously
				GifSequenceWriter writer = new GifSequenceWriter(output, firstImage.getType(), 1, false);

				// write out the first image to our sequence...
				writer.writeToSequence(firstImage);
				for (int i = 1; i < args.length - 1; i++) {
					BufferedImage nextImage = ImageIO.read(new File(args[i]));
					writer.writeToSequence(nextImage);
				}

				writer.close();
				output.close();
			} else {
				System.out.println("Usage: java GifSequenceWriter [list of gif files] [output file]");
			}
		}
	}

	public void SetPathPrefixStructure(String pathPrefix) throws JSONException {
		pathPrefix = pathPrefix.replaceAll("TestName", this.getTestName());
		pathPrefix = pathPrefix.replaceAll("AppName", this.getAppName());
		pathPrefix = pathPrefix.replaceAll("viewport", this.getViewportSize());
		pathPrefix = pathPrefix.replaceAll("hostingOS", this.getHostingOS());
		pathPrefix = pathPrefix.replaceAll("hostingApp", this.getHostingApp());
		prefix = pathPrefix;
	}

	public String getTestName() throws JSONException {
		return this.testData.getJSONObject("startInfo").optString("scenarioName");
	}

	public String getAppName() throws JSONException {
		return this.testData.getJSONObject("startInfo").optString("appName");
	}

	public String getViewportSize() throws JSONException {
		return this.testData.getJSONObject("startInfo").getJSONObject("environment").getJSONObject("displaySize")
				.optString("width").toString() + "x"
				+ this.testData.getJSONObject("startInfo").getJSONObject("environment").getJSONObject("displaySize")
						.optString("height").toString();
	}

	public String getHostingOS() throws JSONException {
		return this.testData.getJSONObject("startInfo").getJSONObject("environment").optString("os");

	}

	public String getHostingApp() throws JSONException {
		return this.testData.getJSONObject("startInfo").getJSONObject("environment").optString("hostingApp");
	}

	public Response runLongRequest(String url, RequestMethod method, String payload, Map<String, String> headers)
			throws InterruptedException {
		Response response = sendRequest(url, method, 1, false, payload, headers);
		return longRequestCheckStatus(response);
	}

	public Response sendRequest(String url, RequestMethod method, int retry, boolean delayBeforeRetry, String payload,
			Map<String, String> headers) throws InterruptedException {

		counter += 1;
		String requestId = counter + "--" + UUID.randomUUID();

		Client client = getJerseyClient();

		Builder request = client.target(url).request().header("x-applitools-eyes-client-request-id", requestId);

		if (null != headers && headers.size() > 0) {
			for (Map.Entry<String, String> entry : headers.entrySet()) {
				request.header(entry.getKey(), entry.getValue());
			}
		}

		Response response = null;

		try {
			switch (method) {
			case GET:
				response = request.get(Response.class);
				break;
			case POST:
				response = request.post(Entity.json(payload));
				break;
			case DELETE:
				response = request.delete(Response.class);
				break;
			default:
				String message = "unimplemented RequestMethod: " + method.toString();
				System.out.println(message);
				break;

			}

			return response;
		} catch (Exception e) {
			String errorMessage = "error message: " + e.getMessage();
			System.out.println(errorMessage);
			e.printStackTrace();

			if (retry > 0) {
				if (delayBeforeRetry) {
					Thread.sleep(RETRY_REQUEST_INTERVAL);
					return sendRequest(url, method, retry - 1, delayBeforeRetry, payload, headers);
				}
				return sendRequest(url, method, retry - 1, delayBeforeRetry, payload, headers);
			}
			throw new Error(errorMessage);
		}

	}

	public Response longRequestCheckStatus(Response responseReceived) throws InterruptedException {
		int status = responseReceived.getStatus();
		String URI;
		switch (status) {
		case HttpStatus.SC_OK:
			return responseReceived;

		case HttpStatus.SC_ACCEPTED:
			URI = responseReceived.getHeaderString("Location") + "?apiKey=" + this.applitoolsViewKey;
			Response requestResponse = longRequestLoop(URI, RequestMethod.GET, LONG_REQUEST_DELAY_MS, null, null);
			return longRequestCheckStatus(requestResponse);
		case HttpStatus.SC_CREATED:
			URI = responseReceived.getHeaderString("Location") + "?apiKey=" + this.applitoolsViewKey;
			return sendRequest(URI, RequestMethod.DELETE, 1, false, null, null);
		case HttpStatus.SC_GONE:
			throw new Error("The server task is gone");
		default:
			throw new Error("Unknown error during long request: " + responseReceived.getStatusInfo().toString());
		}
	}

	public Response longRequestLoop(String url, RequestMethod method, int delay, String payload,
			Map<String, String> headers) throws InterruptedException {
		delay = (int) Math.min(MAX_LONG_REQUEST_DELAY_MS,
				Math.floor(delay * LONG_REQUEST_DELAY_MULTIPLICATIVE_INCREASE_FACTOR));
		System.out.println("Still running... Retrying in " + delay);

		Thread.sleep(delay);
		Response response = sendRequest(url, method, 1, false, payload, headers);

		if (response.getStatus() == HttpStatus.SC_OK) {
			return longRequestLoop(url, method, delay, payload, headers);
		}
		return response;
	}

}
