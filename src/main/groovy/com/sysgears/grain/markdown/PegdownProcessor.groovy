package com.sysgears.grain.markdown

import groovy.util.logging.Slf4j
import org.pegdown.PegDownProcessor

/**
 * Markdown processor which uses PegDown implementation.
 */
@javax.inject.Singleton
@Slf4j
class PegdownProcessor implements MarkdownProcessor {

    /**
     * Process source markdown and returns html 
     *
     * @param source markdown
     *
     * @return html
     */
    public String process(String source) {
        new PegDownProcessor().markdownToHtml(source)
    }

    /**
     * @inheritDoc
     */
    @Override
    public void configChanged() {
    }
}
