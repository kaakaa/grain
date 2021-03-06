/*
 * Copyright (c) 2013 SysGears, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.sysgears.grain.render

import com.sysgears.grain.PerfMetrics
import com.sysgears.grain.asciidoc.AsciiDoctorProcessor
import com.sysgears.grain.config.Config
import com.sysgears.grain.highlight.PageHighlighter
import com.sysgears.grain.markdown.MarkdownProcessor
import com.sysgears.grain.preview.SiteChangeListener
import com.sysgears.grain.registry.HeaderParser
import com.sysgears.grain.registry.ResourceLocator
import com.sysgears.grain.registry.ResourceParser
import com.sysgears.grain.rst.RstProcessor
import com.sysgears.grain.translator.ScriptTranslator

import javax.inject.Inject

/**
 * Grain template engine.
 */
@javax.inject.Singleton
class GrainTemplateEngine implements TemplateEngine, SiteChangeListener {
    
    /** Groovy script name counter */
    private volatile def counter = 1

    /** Site config */
    @Inject private Config config

    /** Site performance metrics */
    @Inject private PerfMetrics perf

    /** Resource locator */
    @Inject private ResourceLocator locator

    /** Source code highlighter */ 
    @Inject private PageHighlighter pageHighlighter

    /** Resource header parser */
    @Inject private HeaderParser headerParser

    /** Script translator */
    @Inject private ScriptTranslator scriptTranslator

    /** Groovy Shell used to execute Groovy scripts */
    @Inject private GroovyShell groovyShell
    
    /** Raw template factory */
    @Inject private RawTemplateFactory rawTemplateFactory

    /** Text template factory */
    @Inject private TextTemplateFactory textTemplateFactory

    /** Groovy script-based template factory */
    @Inject private GroovyTemplateFactory groovyTemplateFactory
    
    /** Markdown processor */
    @Inject private MarkdownProcessor markdownProcessor
    
    /** Rst processor */
    @Inject private RstProcessor rstProcessor

    /** AsciiDoctor processor */
    @Inject private AsciiDoctorProcessor adocProcessor

    /**
     * Clears Groovy Shell cache on site change event to prevent out of memory. 
     */
    @Override
    void siteChanged() {
        clearCache()
    }

    /**
     * Clears Groovy Shell cache on site change event
     */
    private void clearCache() {
        groovyShell.classLoader.clearCache()
    }

    /**
     * @inheritDoc 
     */
    public ResourceTemplate createTemplate(File file) throws RenderException {
        long startDocParse = System.currentTimeMillis()
        def extension = file.getExtension()
        def codeEnabledFiles = config.code_enabled_files
        def codeAllowedFiles = config.code_allowed_files
        if (!codeEnabledFiles || !(codeEnabledFiles instanceof List) || !(extension in codeEnabledFiles)) {
            if (!codeAllowedFiles || !(codeAllowedFiles instanceof List) || !(extension in codeAllowedFiles)) {
                return rawTemplateFactory.create(file)
            }
            def firstLine = new BufferedReader(new FileReader(file)).readLine().trim()
            if (!(firstLine.startsWith("---") || firstLine.startsWith("/*-"))) {
                return rawTemplateFactory.create(file)
            }
        }
        def sourceModifier = config.source_modifier
        def text = sourceModifier ? sourceModifier(file) : file.text as String
        def fragments = []
        if (extension in ['html', 'md', 'markdown', 'rst', 'adoc', 'asciidoctor']) {
            fragments = pageHighlighter.highlight(text)
        }
        text = text.replaceAll(/(?s)```(.*?)```/, '```')

        Map pageConfig = [:]
        new BufferedReader(new StringReader(text)).withReader { reader ->
            def resourceParser = new ResourceParser(file, reader)
            pageConfig = headerParser.parse(file, resourceParser.header)
            text = resourceParser.content
        }
        boolean isMarkup = extension in ['markdown', 'md', 'rst', 'adoc', 'asciidoctor']

        def isScript = pageConfig.script ?: !isMarkup

        if (isMarkup) {
            if (text.contains('${') || text.contains('<%')) {
                isScript = true
            }
            if (extension in ['markdown', 'md']) {
                text = markdownProcessor.process(text)
            } else if (extension in ['rst']) {
                text = rstProcessor.process(text) 
            } else if (extension in ['adoc', 'asciidoctor']) {
                text = adocProcessor.process(text)
            }
        }
        
        long startScriptParse = System.currentTimeMillis()
        perf.docParseTime += (startScriptParse - startDocParse)
        def scriptName = "GrainScript${counter++}.groovy"
        
        def template
        if (isScript) {
            String source = scriptTranslator.translate(text, fragments)
            perf.scriptParseTime += (System.currentTimeMillis() - startScriptParse)
            try {
                long startScriptCompileTime = System.currentTimeMillis()
                def script = groovyShell.parse(source, scriptName)
                template = groovyTemplateFactory.create(file, source, script)
                perf.scriptCompileTime += (System.currentTimeMillis() - startScriptCompileTime)
            } catch (RenderException gr) {
                throw gr
            } catch (t) {
                def sw = new StringWriter()
                t.printStackTrace(new PrintWriter(sw))
                def src = new StringWriter()
                source.readLines().eachWithIndex { String line, int i -> src.append("${i+1}: ${line}\n")}
                throw new RenderException("Failed to parse ${file} script: " + sw.toString() + "\nScript source:\n${src}")
            }
        } else {
            template = textTemplateFactory.create(file, text, fragments)
        }
        
        template
    }
}
