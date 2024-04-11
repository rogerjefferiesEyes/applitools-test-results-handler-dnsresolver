import java.lang.annotation.Target;

import java.io.File;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

import com.applitools.applitools_test_results_handler.ApplitoolsTestResultsHandler;
import com.applitools.applitools_test_results_handler.ResultStatus;
import com.applitools.eyes.RectangleSize;
import com.applitools.eyes.TestResults;
import com.applitools.eyes.config.Configuration;
import com.applitools.eyes.selenium.BrowserType;
import com.applitools.eyes.selenium.Eyes;
import com.applitools.eyes.selenium.fluent.Target;
import com.applitools.eyes.visualgrid.services.RunnerOptions;
import com.applitools.eyes.visualgrid.services.VisualGridRunner;

public class IframeExample {
    public void iframeTest() {
        if (driver.findElements(By.cssSelector("#iframe2theme")).size() > 0) {
            // Get reference to iframe contentDocument
            String brsHook = "function setParentDimensions(element, height = 0, width = 0) {\n"
            		+ "    if (element.parentNode != null && element.parentNode.nodeType == Node.ELEMENT_NODE) {\n"
            		+ "        console.log(`TagName: ${element.parentNode.tagName}`)\n"
            		+ "        if (element.parentNode.tagName.toLowerCase() != 'body') {\n"
            		+ "            var parentHeight = parseInt(element.parentNode.style.height.replace('px', ''));\n"
            		+ "            if (height > 0 && ((typeof (parentHeight) == 'number' && parentHeight < height) || isNaN(parentHeight))) {\n"
            		+ "                console.log(`Setting height for element: ${element.parentNode.tagName} to ${height}` +\n"
            		+ "                    ` (Original Height: ${isNaN(parentHeight) ? 'NOT DETECTED' : parentHeight})`);\n"
            		+ "                element.parentNode.style.height = height + 'px';\n"
            		+ "            }\n"
            		+ "            var parentWidth = parseInt(element.parentNode.style.width.replace('px', ''));\n"
            		+ "            if (width > 0 && (typeof (parentWidth) == 'number' && parentWidth < width)) {\n"
            		+ "                console.log(`Setting width for element: ${element.parentNode.tagName} to ${width}` +\n"
            		+ "                    ` (Original Width: ${parentWidth})`);\n"
            		+ "                element.parentNode.style.width = width + 'px';\n"
            		+ "            }\n"
            		+ "            setParentDimensions(element.parentNode, height, width);\n"
            		+ "        }\n"
            		+ "    }\n"
            		+ "}\n"
            		+ "\n"
            		+ "function getIframeChain(rootDocument, recursionLevel = 0) {\n"
            		+ "    if(rootDocument == null)\n"
            		+ "        return [];\n"
            		+ "    var childIframeChain = []\n"
            		+ "\n"
            		+ "    var childIframeElements = rootDocument.querySelectorAll('iframe');\n"
            		+ "\n"
            		+ "    for (childIframeElement of childIframeElements) {\n"
            		+ "        childIframeChain.push({ iframeElement: childIframeElement, recursionLevel: recursionLevel });\n"
            		+ "\n"
            		+ "        // Capture child iframes recursively, preventing duplicates\n"
            		+ "        childIframeChain = [...new Set(\n"
            		+ "            [\n"
            		+ "                ...childIframeChain,\n"
            		+ "                ...getIframeChain(childIframeElement.contentDocument, recursionLevel + 1)]\n"
            		+ "        )];\n"
            		+ "    }\n"
            		+ "\n"
            		+ "    // Sort captured iframes by recursionLevel, in reverse order\n"
            		+ "    childIframeChain.sort((a, b) => (a.recursionLevel < b.recursionLevel) ? 1 : -1);\n"
            		+ "\n"
            		+ "    return childIframeChain;\n"
            		+ "}\n"
            		+ "\n"
            		+ "function expandIframes(rootDocument = document, isHorizontal = false) {\n"
            		+ "    if (rootDocument) {\n"
            		+ "        \n"
            		+ "        rootDocument.querySelector('body').style.overflow = 'visible';\n"
            		+ "        var iframes = getIframeChain(rootDocument);\n"
            		+ "        for (iframeItem of iframes) {\n"
            		+ "            var iframeElement = iframeItem.iframeElement;\n"
            		+ "            if(iframeElement.contentDocument == null)\n"
            		+ "                continue;\n"
            		+ "            var iframeScrollHeight = iframeElement.contentDocument.querySelector('html').scrollHeight;\n"
            		+ "            var iframeScrollWidth = isHorizontal ? iframeElement.contentDocument.querySelector('html').scrollWidth : 0;\n"
            		+ "            setParentDimensions(iframeElement, iframeScrollHeight, iframeScrollWidth);\n"
            		+ "            iframeElement.style.height = '100%';\n"
            		+ "            if (isHorizontal) {\n"
            		+ "                iframeItem.iframeElement.style.width = '100%';\n"
            		+ "            }\n"
            		+ "            expandIframes(iframeElement.contentDocument);\n"
            		+ "        }\n"
            		+ "    }\n"
            		+ "}\n"
            		+ "\n"
            		+ "expandIframes(document, true);";
        } else {
            // If iframe2theme not detected, use original call to eyes.check(...)
            eyes.check(Target.window().fully()); /* REPLACE WITH ORIGINAL CALL to eyes.check(...) */
        }
    }
}
