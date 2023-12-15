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
            String brsHook = "var iframeDocument = document.querySelector('#iframe2theme').contentDocument;";

            // Get iframe contentDocument scrollHeight
            brsHook += "var iframeScrollHeight = iframeDocument.getElementsByTagName('html')[0].scrollHeight + 'px';";

            // Set top-level body overflow to 'auto', to allow scrolling
            brsHook += "document.querySelector('body').style.overflow = 'auto';";

            // Set height of the <div> container around iframe2theme, to match iframe2theme
            // scrollHeight
            brsHook += "document.querySelector('div#cpo_iframe2').style.height = iframeScrollHeight;";

            // Set iframe height to 100%, to fill its container div
            brsHook += "document.querySelector('#iframe2theme').style.height = '100%';";

            // Ultrafast Grid Visual checkpoint with beforeRenderScreenshotHook
            eyes.check(Target.window().fully().beforeRenderScreenshotHook(brsHook));
        } else {
            // If iframe2theme not detected, use original call to eyes.check(...)
            eyes.check(Target.window().fully()); /* REPLACE WITH ORIGINAL CALL to eyes.check(...) */
        }
    }
}
