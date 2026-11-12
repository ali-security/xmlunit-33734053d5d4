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
package org.xmlunit.transform;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.io.Writer;
import java.util.Properties;
import javax.xml.transform.ErrorListener;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.URIResolver;
import javax.xml.transform.stream.StreamSource;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.w3c.dom.Document;
import org.xmlunit.ConfigurationException;
import org.xmlunit.TestResources;
import org.xmlunit.XMLUnitException;
import org.xmlunit.builder.Input;

import static org.hamcrest.core.IsNot.*;
import static org.hamcrest.core.StringContains.containsString;
import static org.junit.Assert.*;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class TransformationTest {
    private static final String MARKER_PROPERTY =
        "org.xmlunit.transform.TransformationTest.extensionFunctionCalled";

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

    private Transformation t;

    @Mock
    private TransformerFactory fac;

    @Mock
    private Transformer transformer;

    @Before
    public void setupMocks() throws Exception {
        MockitoAnnotations.initMocks(this);
        when(fac.newTransformer(any(Source.class))).thenReturn(transformer);
        when(fac.newTransformer()).thenReturn(transformer);
    }

    @Before
    public void createTransformation() {
        t = new Transformation(Input.fromFile(TestResources.DOG_FILE)
                               .build());
        t.setStylesheet(Input.fromFile(TestResources.ANIMAL_XSL).build());
    }

    @Test public void transformAnimalToString() {
        assertEquals("<?xml version=\"1.0\" encoding=\"UTF-8\"?><dog/>",
                     t.transformToString());
    }

    @Test public void transformAnimalToDocument() {
        Document doc = t.transformToDocument();
        assertEquals("dog", doc.getDocumentElement().getTagName());
    }

    @Test public void transformAnimalToHtml() {
        t.addOutputProperty(OutputKeys.METHOD, "html");
        assertThat(t.transformToString(),
                   not("<?xml version=\"1.0\" encoding=\"UTF-8\"?><dog/>"));
    }

    @Test(expected=IllegalArgumentException.class)
    public void shouldRejectNullSourceInSetSource() {
        Transformation t = new Transformation();
        t.setSource(null);
    }

    @Test(expected=IllegalArgumentException.class)
    public void shouldRejectNullOutputPropertyName() {
        Transformation t = new Transformation();
        t.addOutputProperty(null, "foo");
    }

    @Test(expected=IllegalArgumentException.class)
    public void shouldRejectNullOutputPropertyValue() {
        Transformation t = new Transformation();
        t.addOutputProperty("foo", null);
    }

    @Test(expected=IllegalArgumentException.class)
    public void shouldRejectNullParameterName() {
        Transformation t = new Transformation();
        t.addParameter(null, "foo");
    }

    @Test(expected=ConfigurationException.class)
    public void shouldTransformTransformerConfigurationException() throws Exception {
        when(fac.newTransformer(any(Source.class)))
            .thenThrow(new TransformerConfigurationException());
        t.setFactory(fac);
        t.transformToString();
    }

    @Test
    public void shouldTransformTransformerException() throws Exception {
        doThrow(new TransformerException("foo"))
            .when(transformer)
            .transform(any(Source.class), any(Result.class));
        t.setFactory(fac);
        try {
            t.transformToString();
            fail("should have thrown XMLUnitException");
        } catch (XMLUnitException ex) {
            // assert this is not a XMLUnitException subclass
            assertEquals(XMLUnitException.class, ex.getClass());
        }
    }

    @Test
    public void shouldCallNoArgNewTransformerWithoutStylesheet() throws Exception {
        Transformation t = new Transformation(Input.fromFile(TestResources.DOG_FILE)
                                              .build());
        t.setFactory(fac);
        t.transformToString();

        verify(fac).newTransformer();
    }

    @Test
    public void passesThroughOutputProperties() {
        t.setFactory(fac);
        t.addOutputProperty(OutputKeys.METHOD, "html");
        t.transformToString();

        Properties p = new Properties();
        p.setProperty(OutputKeys.METHOD, "html");
        verify(transformer).setOutputProperties(p);
    }

    @Test
    public void clearsOutputProperties() {
        t.setFactory(fac);
        t.addOutputProperty(OutputKeys.METHOD, "html");
        t.clearOutputProperties();
        t.transformToString();

        Properties p = new Properties();
        p.setProperty(OutputKeys.METHOD, "html");
        verify(transformer, never()).setOutputProperties(p);
        verify(transformer).setOutputProperties(new Properties());
    }

    @Test
    public void passesThroughParameters() {
        t.setFactory(fac);
        t.addParameter(OutputKeys.METHOD, "html");
        t.transformToString();

        verify(transformer).setParameter(OutputKeys.METHOD, "html");
    }

    @Test
    public void clearsParameters() {
        t.setFactory(fac);
        t.addParameter(OutputKeys.METHOD, "html");
        t.clearParameters();
        t.transformToString();

        verify(transformer, never()).setParameter(OutputKeys.METHOD, "html");
    }

    @Test
    public void passesThroughURIResolver() {
        URIResolver u = mock(URIResolver.class);
        t.setFactory(fac);
        t.setURIResolver(u);
        t.transformToString();

        verify(transformer).setURIResolver(u);
    }

    @Test
    public void passesThroughErrorListener() {
        ErrorListener e = mock(ErrorListener.class);
        t.setFactory(fac);
        t.setErrorListener(e);
        t.transformToString();

        verify(transformer).setErrorListener(e);
    }

    @Test(expected=IllegalStateException.class)
    public void shouldRejectNullSourceInTransform() {
        Transformation t = new Transformation();
        t.transformToString();
    }

    @Test(expected=IllegalArgumentException.class)
    public void shouldRejectNullResult() {
        t.transformTo(null);
    }

    @Test
    public void shouldNotExecuteExtensionFunctionsByDefault() {
        // setProperty returns the old value, make sure it is not null
        System.setProperty(MARKER_PROPERTY, "untouched");
        try {
            Transformation t = new Transformation(new StreamSource(new StringReader("<foo/>")));
            t.setStylesheet(new StreamSource(new StringReader(EXTENSION_FUNCTION_XSL)));
            try {
                t.transformToString();
            } catch (XMLUnitException ex) {
                // the processor refused to execute the extension function
            }
            assertEquals("extension function must not have been executed",
                         "untouched", System.getProperty(MARKER_PROPERTY));
        } finally {
            System.clearProperty(MARKER_PROPERTY);
        }
    }

    @Test
    public void shouldExecuteExtensionFunctionsUsingExplicitlySetFactory() {
        // sanity check: the stylesheet really executes code on this runtime
        System.setProperty(MARKER_PROPERTY, "untouched");
        try {
            Transformation t = new Transformation(new StreamSource(new StringReader("<foo/>")));
            t.setStylesheet(new StreamSource(new StringReader(EXTENSION_FUNCTION_XSL)));
            t.setFactory(TransformerFactory.newInstance());
            t.transformToString();
            assertEquals("pwned", System.getProperty(MARKER_PROPERTY));
        } finally {
            System.clearProperty(MARKER_PROPERTY);
        }
    }

    @Test
    public void shouldStillLoadExternalStylesheetsByDefault() throws Exception {
        write(folder.newFile("imported.xsl"),
              "<xsl:stylesheet version=\"1.0\" xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\">"
              + "<xsl:template match=\"/\"><imported/></xsl:template>"
              + "</xsl:stylesheet>");
        File main = folder.newFile("main.xsl");
        write(main, "<xsl:stylesheet version=\"1.0\" xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\">"
              + "<xsl:import href=\"imported.xsl\"/>"
              + "</xsl:stylesheet>");
        Transformation t = new Transformation(new StreamSource(new StringReader("<foo/>")));
        t.setStylesheet(new StreamSource(main));
        t.addOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        assertEquals("<imported/>", t.transformToString());
    }

    @Test
    public void shouldStillLoadExternalDTDsByDefault() throws Exception {
        write(folder.newFile("entity.dtd"), "<!ENTITY greeting \"hello\">");
        File doc = folder.newFile("doc.xml");
        write(doc, "<!DOCTYPE foo SYSTEM \"entity.dtd\"><foo>&greeting;</foo>");
        Transformation t = new Transformation(new StreamSource(doc));
        t.addOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        assertThat(t.transformToString(), containsString("<foo>hello</foo>"));
    }

    private static void write(File f, String content) throws IOException {
        Writer w = new OutputStreamWriter(new FileOutputStream(f), "UTF-8");
        try {
            w.write(content);
        } finally {
            w.close();
        }
    }
}
