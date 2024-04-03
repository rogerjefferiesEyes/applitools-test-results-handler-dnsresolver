function setParentDimensions(element, height = 0) {
    if (element.parentNode != null && element.parentNode.nodeType == Node.ELEMENT_NODE && height > 0) {
        console.log(`TagName: ${element.parentNode.tagName}`)
        if (element.parentNode.tagName.toLowerCase() != 'body') {
            var parentHeight = parseInt(element.parentNode.style.height.replace('px', ''));
            if ((typeof (parentHeight) == 'number' && parentHeight < height) || isNaN(parentHeight)) {
                console.log(`Setting height for element: ${element.parentNode.tagName} to ${height}` +
                ` (Original Height: ${isNaN(parentHeight) ? 'NOT DETECTED' : parentHeight})`);
                element.parentNode.style.height = height + 'px';
            }
            setParentDimensions(element.parentNode, height);
        }
    }
}

function getIframeChain(rootDocument, recursionLevel = 0) {
    var childIframeChain = []

    var childIframeElements = rootDocument.querySelectorAll('iframe');

    for (childIframeElement of childIframeElements) {
        childIframeChain.push({ iframeElement: childIframeElement, recursionLevel: recursionLevel });

        // Capture child iframes recursively, preventing duplicates
        childIframeChain = [...new Set(
            [
                ...childIframeChain,
                ...getIframeChain(childIframeElement.contentDocument, recursionLevel + 1)]
        )];
    }

    // Sort captured iframes by recursionLevel, in reverse order
    childIframeChain.sort((a, b) => (a.recursionLevel < b.recursionLevel) ? 1 : -1);

    return childIframeChain;
}

function expandIframes(rootDocument = document, isHorizontal = false) {
    if (rootDocument) {
        rootDocument.querySelector('body').style.overflow = 'visible';
        var iframes = getIframeChain(rootDocument);
        for (iframeItem of iframes) {
            var iframeElement = iframeItem.iframeElement;
            if(iframeElement.contentDocument == null || iframeElement.style.display == 'none')
                continue;
            var iframeScrollHeight = iframeElement.contentDocument.querySelector('html').scrollHeight;
            var iframeScrollWidth = isHorizontal ? iframeElement.contentDocument.querySelector('html').scrollWidth : 0;
            setParentDimensions(iframeElement, iframeScrollHeight, iframeScrollWidth);
            iframeElement.style.height = '100%';
            if (isHorizontal) {
                iframeItem.iframeElement.style.width = '100%';
            }
            expandIframes(iframeElement.contentDocument);
        }
    }
}
expandIframes();