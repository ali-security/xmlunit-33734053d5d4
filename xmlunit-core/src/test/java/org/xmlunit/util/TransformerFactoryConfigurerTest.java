/*
  This file is licensed to You under the Apache License, Version 2.0
  (the "License"); you may not use this file except in compliance with
  the License.  You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
*/
package org.xmlunit.util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.xml.XMLConstants;
import javax.xml.transform.ErrorListener;
import javax.xml.transform.Source;
import javax.xml.transform.Templates;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.URIResolver;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.xmlunit.ConfigurationException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class TransformerFactoryConfigurerTest {

    private static final String ACCESS_EXTERNAL_DTD =
        "http://javax.xml.XMLConstants/property/accessExternalDTD";
    private static final String ACCESS_EXTERNAL_STYLESHEET =
        "http://javax.xml.XMLConstants/property/accessExternalStylesheet";
    private static final String ENABLE_EXTENSION_FUNCTIONS = "jdk.xml.enableExtensionFunctions";

    private static final String MARKER_PROPERTY =
        "org.xmlunit.util.TransformerFactoryConfigurerTest.extensionFunctionCalled";

    /**
     * Stylesheet using a Java extension function to execute code -
     * it sets a system property as a visible side effect.
     */
    private static final String EXTENSION_FUNCTION_XSL =
        "<xsl:stylesheet version=\"1.0\""
        + " xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\""
        + " xmlns:java=\"http://xml.apache.org/xalan/java\">"
        + "<xsl:template match=\"/\">"
        + "<result><xsl:value-of select=\"java:java.lang.System.setProperty('"
        + MARKER_PROPERTY + "', 'pwned')\"/></result>"
        + "</xsl:template>"
        + "</xsl:stylesheet>";

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Before
    public void setMarker() {
        // setProperty returns the old value, make sure it is not null
        System.setProperty(MARKER_PROPERTY, "untouched");
    }

    @After
    public void clearMarker() {
        System.clearProperty(MARKER_PROPERTY);
    }

    @Test
    public void appliesAttributesAndFeatures() {
        RecordingTransformerFactory fac = new RecordingTransformerFactory();
        TransformerFactory configured = TransformerFactoryConfigurer.builder()
            .withAttribute("a", "b")
            .withSafeAttribute("c", "d")
            .withFeature("e", true)
            .withSafeFeature("f", false)
            .build()
            .configure(fac);
        assertSame(fac, configured);
        assertEquals("b", fac.attributes.get("a"));
        assertEquals("d", fac.attributes.get("c"));
        assertEquals(Boolean.TRUE, fac.features.get("e"));
        assertEquals(Boolean.FALSE, fac.features.get("f"));
    }

    @Test(expected = ConfigurationException.class)
    public void unsupportedAttributeCausesException() {
        TransformerFactoryConfigurer.builder()
            .withAttribute("a", "b")
            .build()
            .configure(new RecordingTransformerFactory("a"));
    }

    @Test
    public void unsupportedSafeAttributeIsIgnored() {
        RecordingTransformerFactory fac = new RecordingTransformerFactory("a");
        TransformerFactoryConfigurer.builder()
            .withSafeAttribute("a", "b")
            .withSafeAttribute("c", "d")
            .build()
            .configure(fac);
        assertFalse(fac.attributes.containsKey("a"));
        assertEquals("d", fac.attributes.get("c"));
    }

    @Test(expected = ConfigurationException.class)
    public void unsupportedFeatureCausesException() {
        TransformerFactoryConfigurer.builder()
            .withFeature("a", true)
            .build()
            .configure(new RecordingTransformerFactory("a"));
    }

    @Test
    public void unsupportedSafeFeatureIsIgnored() {
        RecordingTransformerFactory fac = new RecordingTransformerFactory("a");
        TransformerFactoryConfigurer.builder()
            .withSafeFeature("a", true)
            .withSafeFeature("c", true)
            .build()
            .configure(fac);
        assertFalse(fac.features.containsKey("a"));
        assertEquals(Boolean.TRUE, fac.features.get("c"));
    }

    @Test
    public void defaultDisablesDTDLoadingAndExtensionFunctions() {
        RecordingTransformerFactory fac = new RecordingTransformerFactory();
        TransformerFactoryConfigurer.Default.configure(fac);
        assertEquals("", fac.attributes.get(ACCESS_EXTERNAL_DTD));
        assertEquals("false", fac.attributes.get(ENABLE_EXTENSION_FUNCTIONS));
        assertEquals("all", fac.attributes.get(ACCESS_EXTERNAL_STYLESHEET));
        assertTrue(fac.features.isEmpty());
    }

    @Test
    public void noExternalAccessDisablesDTDsStylesheetsAndExtensionFunctions() {
        RecordingTransformerFactory fac = new RecordingTransformerFactory();
        TransformerFactoryConfigurer.NoExternalAccess.configure(fac);
        assertEquals("", fac.attributes.get(ACCESS_EXTERNAL_DTD));
        assertEquals("", fac.attributes.get(ACCESS_EXTERNAL_STYLESHEET));
        assertEquals("false", fac.attributes.get(ENABLE_EXTENSION_FUNCTIONS));
        assertTrue(fac.features.isEmpty());
    }

    @Test
    public void secureProcessingEnablesSecureProcessing() {
        RecordingTransformerFactory fac = new RecordingTransformerFactory();
        TransformerFactoryConfigurer.SecureProcessing.configure(fac);
        assertEquals(Boolean.TRUE, fac.features.get(XMLConstants.FEATURE_SECURE_PROCESSING));
        assertFalse(fac.attributes.containsKey(ENABLE_EXTENSION_FUNCTIONS));
    }

    @Test
    public void noDtdButExtensionFunctionsOnlyDisablesDTDLoading() {
        RecordingTransformerFactory fac = new RecordingTransformerFactory();
        TransformerFactoryConfigurer.NoDtdButExtensionFunctions.configure(fac);
        assertEquals("", fac.attributes.get(ACCESS_EXTERNAL_DTD));
        assertEquals("all", fac.attributes.get(ACCESS_EXTERNAL_STYLESHEET));
        assertFalse(fac.attributes.containsKey(ENABLE_EXTENSION_FUNCTIONS));
        assertTrue(fac.features.isEmpty());
    }

    @Test
    public void noExternalAccessButExtensionFunctionsDoesntDisableExtensionFunctions() {
        RecordingTransformerFactory fac = new RecordingTransformerFactory();
        TransformerFactoryConfigurer.NoExternalAccessButExtensionFunctions.configure(fac);
        assertEquals("", fac.attributes.get(ACCESS_EXTERNAL_DTD));
        assertEquals("", fac.attributes.get(ACCESS_EXTERNAL_STYLESHEET));
        assertFalse(fac.attributes.containsKey(ENABLE_EXTENSION_FUNCTIONS));
        assertTrue(fac.features.isEmpty());
    }

    @Test
    public void defaultFallsBackToSecureProcessingButStillAllowsExternalStylesheets() {
        RecordingTransformerFactory fac = new RecordingTransformerFactory(ENABLE_EXTENSION_FUNCTIONS);
        TransformerFactoryConfigurer.Default.configure(fac);
        assertEquals(Boolean.TRUE, fac.features.get(XMLConstants.FEATURE_SECURE_PROCESSING));
        assertEquals("", fac.attributes.get(ACCESS_EXTERNAL_DTD));
        assertEquals("all", fac.attributes.get(ACCESS_EXTERNAL_STYLESHEET));
    }

    @Test
    public void noExternalAccessFallsBackToSecureProcessing() {
        RecordingTransformerFactory fac = new RecordingTransformerFactory(ENABLE_EXTENSION_FUNCTIONS);
        TransformerFactoryConfigurer.NoExternalAccess.configure(fac);
        assertEquals(Boolean.TRUE, fac.features.get(XMLConstants.FEATURE_SECURE_PROCESSING));
        assertEquals("", fac.attributes.get(ACCESS_EXTERNAL_DTD));
        assertEquals("", fac.attributes.get(ACCESS_EXTERNAL_STYLESHEET));
    }

    @Test
    public void extensionFunctionsDisabledFallbackKeepsExternalAccessSettings() {
        RecordingTransformerFactory fac = new RecordingTransformerFactory(ENABLE_EXTENSION_FUNCTIONS);
        fac.attributes.put(ACCESS_EXTERNAL_DTD, "file");
        fac.attributes.put(ACCESS_EXTERNAL_STYLESHEET, "jar");
        TransformerFactoryConfigurer.builder()
            .withExtensionFunctionsDisabled()
            .build()
            .configure(fac);
        assertEquals(Boolean.TRUE, fac.features.get(XMLConstants.FEATURE_SECURE_PROCESSING));
        assertEquals("file", fac.attributes.get(ACCESS_EXTERNAL_DTD));
        assertEquals("jar", fac.attributes.get(ACCESS_EXTERNAL_STYLESHEET));
    }

    @Test
    public void extensionFunctionsDisabledIgnoresUnsupportedSecureProcessing() {
        RecordingTransformerFactory fac =
            new RecordingTransformerFactory(ENABLE_EXTENSION_FUNCTIONS, XMLConstants.FEATURE_SECURE_PROCESSING,
                                            ACCESS_EXTERNAL_DTD, ACCESS_EXTERNAL_STYLESHEET);
        TransformerFactoryConfigurer.Default.configure(fac);
        assertTrue(fac.features.isEmpty());
        assertTrue(fac.attributes.isEmpty());
    }

    // tests using the TransformerFactory of the runtime

    @Test
    public void extensionFunctionsAreExecutedByUnconfiguredFactory() throws Exception {
        // sanity check: the stylesheet really executes code on this runtime
        transformWithExtensionFunction(TransformerFactory.newInstance());
        assertEquals("pwned", System.getProperty(MARKER_PROPERTY));
    }

    @Test
    public void defaultDoesntExecuteExtensionFunctions() {
        assertExtensionFunctionBlocked(TransformerFactoryConfigurer.Default);
    }

    @Test
    public void noExternalAccessDoesntExecuteExtensionFunctions() {
        assertExtensionFunctionBlocked(TransformerFactoryConfigurer.NoExternalAccess);
    }

    @Test
    public void secureProcessingDoesntExecuteExtensionFunctions() {
        assertExtensionFunctionBlocked(TransformerFactoryConfigurer.SecureProcessing);
    }

    @Test
    public void builderWithExtensionFunctionsDisabledDoesntExecuteExtensionFunctions() {
        assertExtensionFunctionBlocked(TransformerFactoryConfigurer.builder()
                                       .withExtensionFunctionsDisabled()
                                       .build());
    }

    @Test
    public void noDtdButExtensionFunctionsExecutesExtensionFunctions() throws Exception {
        transformWithExtensionFunction(TransformerFactoryConfigurer.NoDtdButExtensionFunctions
                                       .configure(TransformerFactory.newInstance()));
        assertEquals("pwned", System.getProperty(MARKER_PROPERTY));
    }

    @Test
    public void noExternalAccessButExtensionFunctionsExecutesExtensionFunctions() throws Exception {
        transformWithExtensionFunction(TransformerFactoryConfigurer.NoExternalAccessButExtensionFunctions
                                       .configure(TransformerFactory.newInstance()));
        assertEquals("pwned", System.getProperty(MARKER_PROPERTY));
    }

    @Test
    public void extensionFunctionsDisabledKeepsExternalAccessSettingsOfRuntime() {
        TransformerFactory plain = TransformerFactory.newInstance();
        TransformerFactory configured = TransformerFactoryConfigurer.builder()
            .withExtensionFunctionsDisabled()
            .build()
            .configure(TransformerFactory.newInstance());
        assertEquals(getAttributeOrNull(plain, ACCESS_EXTERNAL_DTD),
                     getAttributeOrNull(configured, ACCESS_EXTERNAL_DTD));
        assertEquals(getAttributeOrNull(plain, ACCESS_EXTERNAL_STYLESHEET),
                     getAttributeOrNull(configured, ACCESS_EXTERNAL_STYLESHEET));
    }

    @Test
    public void defaultConfigurerStillAllowsExternalStylesheets() throws Exception {
        TransformerFactory fac = TransformerFactoryConfigurer.Default.configure(TransformerFactory.newInstance());
        assertEquals("<imported/>", transformWithImportedStylesheet(fac));
    }

    @Test
    public void noExternalAccessDoesntLoadExternalStylesheets() throws Exception {
        if (getAttributeOrNull(TransformerFactory.newInstance(), ACCESS_EXTERNAL_STYLESHEET) == null) {
            // runtime doesn't support restricting external stylesheet access
            return;
        }
        TransformerFactory fac =
            TransformerFactoryConfigurer.NoExternalAccess.configure(TransformerFactory.newInstance());
        try {
            transformWithImportedStylesheet(fac);
            fail("expected external stylesheet to be rejected");
        } catch (TransformerException ex) {
            // expected
        }
    }

    private void assertExtensionFunctionBlocked(TransformerFactoryConfigurer configurer) {
        TransformerFactory fac = configurer.configure(TransformerFactory.newInstance());
        try {
            transformWithExtensionFunction(fac);
        } catch (TransformerException ex) {
            // the processor refused to execute the extension function
        }
        assertEquals("extension function must not have been executed",
                     "untouched", System.getProperty(MARKER_PROPERTY));
    }

    private static String transformWithExtensionFunction(TransformerFactory fac) throws TransformerException {
        fac.setErrorListener(new QuietErrorListener());
        Transformer t = fac.newTransformer(new StreamSource(new StringReader(EXTENSION_FUNCTION_XSL)));
        StringWriter sw = new StringWriter();
        t.transform(new StreamSource(new StringReader("<foo/>")), new StreamResult(sw));
        return sw.toString();
    }

    private String transformWithImportedStylesheet(TransformerFactory fac) throws Exception {
        File imported = folder.newFile("imported.xsl");
        write(imported, "<xsl:stylesheet version=\"1.0\" xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\">"
              + "<xsl:template match=\"/\"><imported/></xsl:template>"
              + "</xsl:stylesheet>");
        File main = folder.newFile("main.xsl");
        write(main, "<xsl:stylesheet version=\"1.0\" xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\">"
              + "<xsl:import href=\"imported.xsl\"/>"
              + "<xsl:output omit-xml-declaration=\"yes\"/>"
              + "</xsl:stylesheet>");
        fac.setErrorListener(new QuietErrorListener());
        Transformer t = fac.newTransformer(new StreamSource(main));
        StringWriter sw = new StringWriter();
        t.transform(new StreamSource(new StringReader("<foo/>")), new StreamResult(sw));
        return sw.toString();
    }

    private static void write(File f, String content) throws IOException {
        Writer w = new OutputStreamWriter(new FileOutputStream(f), "UTF-8");
        try {
            w.write(content);
        } finally {
            w.close();
        }
    }

    private static Object getAttributeOrNull(TransformerFactory fac, String name) {
        try {
            return fac.getAttribute(name);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static class QuietErrorListener implements ErrorListener {
        public void warning(TransformerException exception) {
        }
        public void error(TransformerException exception) throws TransformerException {
            throw exception;
        }
        public void fatalError(TransformerException exception) throws TransformerException {
            throw exception;
        }
    }

    /**
     * TransformerFactory recording the attributes and features
     * set, rejecting the configured names.
     *
     * <p>Mimics the JDK's TransformerFactory in so far as enabling
     * secure processing restricts external access.</p>
     */
    private static class RecordingTransformerFactory extends TransformerFactory {
        private final Map<String, Object> attributes = new HashMap<String, Object>();
        private final Map<String, Boolean> features = new HashMap<String, Boolean>();
        private final Set<String> unsupported;

        private RecordingTransformerFactory(String... unsupported) {
            this.unsupported = new HashSet<String>(Arrays.asList(unsupported));
            if (!this.unsupported.contains(ACCESS_EXTERNAL_DTD)) {
                attributes.put(ACCESS_EXTERNAL_DTD, "all");
            }
            if (!this.unsupported.contains(ACCESS_EXTERNAL_STYLESHEET)) {
                attributes.put(ACCESS_EXTERNAL_STYLESHEET, "all");
            }
        }

        @Override
        public void setAttribute(String name, Object value) {
            if (unsupported.contains(name)) {
                throw new IllegalArgumentException("unsupported attribute " + name);
            }
            attributes.put(name, value);
        }

        @Override
        public Object getAttribute(String name) {
            if (unsupported.contains(name) || !attributes.containsKey(name)) {
                throw new IllegalArgumentException("unsupported attribute " + name);
            }
            return attributes.get(name);
        }

        @Override
        public void setFeature(String name, boolean value) throws TransformerConfigurationException {
            if (unsupported.contains(name)) {
                throw new TransformerConfigurationException("unsupported feature " + name);
            }
            features.put(name, value);
            if (XMLConstants.FEATURE_SECURE_PROCESSING.equals(name) && value) {
                attributes.put(ACCESS_EXTERNAL_DTD, "");
                attributes.put(ACCESS_EXTERNAL_STYLESHEET, "");
            }
        }

        @Override
        public boolean getFeature(String name) {
            Boolean b = features.get(name);
            return b != null && b.booleanValue();
        }

        @Override
        public Transformer newTransformer(Source source) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Transformer newTransformer() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Templates newTemplates(Source source) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Source getAssociatedStylesheet(Source source, String media, String title, String charset) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setURIResolver(URIResolver resolver) {
        }

        @Override
        public URIResolver getURIResolver() {
            return null;
        }

        @Override
        public void setErrorListener(ErrorListener listener) {
        }

        @Override
        public ErrorListener getErrorListener() {
            return null;
        }
    }
}
