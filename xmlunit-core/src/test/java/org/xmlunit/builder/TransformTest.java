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
package org.xmlunit.builder;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.dom.DOMResult;
import org.xmlunit.TestResources;
import org.xmlunit.XMLUnitException;
import org.xmlunit.builder.Input;
import org.junit.Test;
import org.w3c.dom.Document;

import static org.hamcrest.core.IsNot.*;
import static org.junit.Assert.*;

public class TransformTest {

    @Test public void transformAnimalToString() {
        assertEquals("<?xml version=\"1.0\" encoding=\"UTF-8\"?><dog/>",
                     Transform
                     .source(Input.fromFile(TestResources.DOG_FILE)
                             .build())
                     .withStylesheet(Input.fromFile(TestResources.ANIMAL_XSL)
                                     .build())
                     .build()
                     .toString());
    }

    @Test public void transformAnimalToDocument() {
        Document doc = Transform
            .source(Input.fromFile(TestResources.DOG_FILE).build())
            .withStylesheet(Input.fromFile(TestResources.ANIMAL_XSL)
                            .build())
            .withParameter("foo", "bar")
            .build()
            .toDocument();
        assertEquals("dog", doc.getDocumentElement().getTagName());
    }

    @Test public void transformAnimalToHtml() {
        assertThat(Transform
                   .source(Input.fromFile(TestResources.DOG_FILE).build())
                   .withStylesheet(Input.fromFile(TestResources.ANIMAL_XSL)
                                   .build())
                   .withOutputProperty(OutputKeys.METHOD, "html")
                   .build()
                   .toString(),
                   not("<?xml version=\"1.0\" encoding=\"UTF-8\"?><dog/>"));
    }

    @Test public void transformAnimalToDOMResult() {
        DOMResult r = new DOMResult();
        Transform
            .source(Input.fromFile(TestResources.DOG_FILE).build())
            .withStylesheet(Input.fromFile(TestResources.ANIMAL_XSL)
                            .build())
            .build()
            .to(r);
        assertEquals("dog", ((Document) r.getNode()).getDocumentElement().getTagName());
    }

    @Test public void doesntExecuteExtensionFunctionsByDefault() {
        String marker = "org.xmlunit.builder.TransformTest.extensionFunctionCalled";
        // setProperty returns the old value, make sure it is not null
        System.setProperty(marker, "untouched");
        try {
            try {
                Transform
                    .source(Input.fromString("<foo/>").build())
                    .withStylesheet(Input.fromString(
                        "<xsl:stylesheet version=\"1.0\""
                        + " xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\""
                        + " xmlns:java=\"http://xml.apache.org/xalan/java\">"
                        + "<xsl:template match=\"/\">"
                        + "<result><xsl:value-of select=\"java:java.lang.System.setProperty('"
                        + marker + "', 'pwned')\"/></result>"
                        + "</xsl:template>"
                        + "</xsl:stylesheet>").build())
                    .build()
                    .toString();
            } catch (XMLUnitException ex) {
                // the processor refused to execute the extension function
            }
            assertEquals("extension function must not have been executed",
                         "untouched", System.getProperty(marker));
        } finally {
            System.clearProperty(marker);
        }
    }

}

