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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import javax.xml.XMLConstants;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerFactory;

import org.xmlunit.ConfigurationException;

/**
 * Configures TransformerFactories.
 */
public class TransformerFactoryConfigurer {

    // XMLConstants.ACCESS_EXTERNAL_DTD is not available in Java 6
    private static final String ACCESS_EXTERNAL_DTD =
        "http://javax.xml.XMLConstants/property/accessExternalDTD";
    // XMLConstants.ACCESS_EXTERNAL_STYLESHEET is not available in Java 6
    private static final String ACCESS_EXTERNAL_STYLESHEET =
        "http://javax.xml.XMLConstants/property/accessExternalStylesheet";
    private static final String ENABLE_EXTENSION_FUNCTIONS = "jdk.xml.enableExtensionFunctions";

    private final Map<String, Object> attributes, safeAttributes;
    private final Map<String, Boolean> features, safeFeatures;
    private final boolean extensionFunctionsDisabled;

    private TransformerFactoryConfigurer(Map<String, Object> attributes, Map<String, Object> safeAttributes,
            Map<String, Boolean> features, Map<String, Boolean> safeFeatures,
            boolean extensionFunctionsDisabled) {
        this.attributes = attributes;
        this.safeAttributes = safeAttributes;
        this.features = features;
        this.safeFeatures = safeFeatures;
        this.extensionFunctionsDisabled = extensionFunctionsDisabled;
    }

    /**
     * Applies the current configuration.
     *
     * @param factory the factory to configure
     * @return the configured factory
     * @throws ConfigurationException if any of the attributes or
     * features set is not supported.
     */
    public TransformerFactory configure(TransformerFactory factory) {
        for (Map.Entry<String, Object> attr : attributes.entrySet()) {
            try {
                factory.setAttribute(attr.getKey(), attr.getValue());
            } catch (IllegalArgumentException ex) {
                throw new ConfigurationException(ex);
            }
        }
        for (Map.Entry<String, Object> attr : safeAttributes.entrySet()) {
            try {
                factory.setAttribute(attr.getKey(), attr.getValue());
            } catch (IllegalArgumentException ex) {
                // swallow
            }
        }
        for (Map.Entry<String, Boolean> feat : features.entrySet()) {
            try {
                factory.setFeature(feat.getKey(), feat.getValue());
            } catch (TransformerConfigurationException ex) {
                throw new ConfigurationException(ex);
            }
        }
        for (Map.Entry<String, Boolean> feat : safeFeatures.entrySet()) {
            try {
                factory.setFeature(feat.getKey(), feat.getValue());
            } catch (TransformerConfigurationException ex) {
                // swallow
            }
        }
        if (extensionFunctionsDisabled) {
            disableExtensionFunctions(factory);
        }
        return factory;
    }

    /**
     * Disables extension functions using the {@code
     * jdk.xml.enableExtensionFunctions} attribute.
     *
     * <p>TransformerFactories that don't support this attribute -
     * like the ones of Java 8 and earlier - can only disable
     * extension functions by enabling secure processing. As secure
     * processing may restrict external access as a side effect, the
     * external access settings in effect before are restored
     * afterwards.</p>
     */
    private static void disableExtensionFunctions(TransformerFactory factory) {
        try {
            factory.setAttribute(ENABLE_EXTENSION_FUNCTIONS, "false");
            return;
        } catch (IllegalArgumentException ex) {
            // attribute is not supported, fall back to secure processing
        }
        Object externalDTDAccess = getAttributeIfSupported(factory, ACCESS_EXTERNAL_DTD);
        Object externalStylesheetAccess = getAttributeIfSupported(factory, ACCESS_EXTERNAL_STYLESHEET);
        try {
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        } catch (TransformerConfigurationException ex) {
            // swallow
            return;
        }
        setAttributeIfSupported(factory, ACCESS_EXTERNAL_DTD, externalDTDAccess);
        setAttributeIfSupported(factory, ACCESS_EXTERNAL_STYLESHEET, externalStylesheetAccess);
    }

    private static Object getAttributeIfSupported(TransformerFactory factory, String key) {
        try {
            return factory.getAttribute(key);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static void setAttributeIfSupported(TransformerFactory factory, String key, Object value) {
        if (value != null) {
            try {
                factory.setAttribute(key, value);
            } catch (IllegalArgumentException ex) {
                // swallow
            }
        }
    }

    /**
     * Creates a builder for TransformerFactoryConfigurers.
     * @return a fresh builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * The default instance which disables DTD loading and extension functions but still
     * allows loading of external stylesheets.
     */
    public static final TransformerFactoryConfigurer Default = builder()
        .withDTDLoadingDisabled()
        .withExtensionFunctionsDisabled()
        .build();

    /**
     * The instance which enables secure processing thus disables all external access as well as execution of extension
     * functions.
     */
    public static final TransformerFactoryConfigurer SecureProcessing = builder()
        .withSecureProcessingEnabled()
        .build();

    /**
     * The instance which disables DTD loading as well as loading of
     * external stylesheets or extension functions.
     */
    public static final TransformerFactoryConfigurer NoExternalAccess = builder()
        .withDTDLoadingDisabled()
        .withExternalStylesheetLoadingDisabled()
        .withExtensionFunctionsDisabled()
        .build();

    /**
     * The instance which disables DTD loading but still
     * allows loading of external stylesheets and extension functions.
     */
    public static final TransformerFactoryConfigurer NoDtdButExtensionFunctions = builder()
        .withDTDLoadingDisabled()
        .build();

    /**
     * The instance which disables DTD loading as well as loading of
     * external stylesheets but allows extension functions.
     */
    public static final TransformerFactoryConfigurer NoExternalAccessButExtensionFunctions = builder()
        .withDTDLoadingDisabled()
        .withExternalStylesheetLoadingDisabled()
        .build();

    /**
     * Builder for a TransformerFactoryConfigurer.
     */
    public static class Builder {
        private final Map<String, Object> attributes = new HashMap<String, Object>();
        private final Map<String, Object> safeAttributes = new HashMap<String, Object>();
        private final Map<String, Boolean> features = new HashMap<String, Boolean>();
        private final Map<String, Boolean> safeFeatures = new HashMap<String, Boolean>();
        private boolean extensionFunctionsDisabled;

        /**
         * Builds a TransformerFactoryConfigurer.
         * @return the configurer
         */
        public TransformerFactoryConfigurer build() {
            return new TransformerFactoryConfigurer(Collections.unmodifiableMap(attributes),
                Collections.unmodifiableMap(safeAttributes), Collections.unmodifiableMap(features),
                Collections.unmodifiableMap(safeFeatures), extensionFunctionsDisabled);
        }

        /**
         * Configures the factory with the given attribute, causes an
         * exception in {@link #configure} if the attribute is not
         * supported.
         * @param key key of the attribute to be set
         * @param value value for the attribute to set
         * @return this
         */
        public Builder withAttribute(String key, Object value) {
            attributes.put(key, value);
            return this;
        }

        /**
         * Configures the factory with the given attribute if it is
         * supported.
         * @param key key of the attribute to be set
         * @param value value for the attribute to set
         * @return this
         */
        public Builder withSafeAttribute(String key, Object value) {
            safeAttributes.put(key, value);
            return this;
        }

        /**
         * Configures the factory with the given feature, causes an
         * exception in {@link #configure} if the feature is not
         * supported.
         * @param key key of the feature to be set
         * @param value value for the feature to set
         * @return this
         */
        public Builder withFeature(String key, boolean value) {
            features.put(key, value);
            return this;
        }

        /**
         * Configures the factory with the given feature if it is
         * supported.
         * @param key key of the feature to be set
         * @param value value for the feature to set
         * @return this
         */
        public Builder withSafeFeature(String key, boolean value) {
            safeFeatures.put(key, value);
            return this;
        }

        /**
         * Configures the factory to not load any external DTDs.
         * @return this
         */
        public Builder withDTDLoadingDisabled() {
            return withSafeAttribute(ACCESS_EXTERNAL_DTD, "");
        }

        /**
         * Configures the factory to not load any external stylesheets.
         * @return this
         */
        public Builder withExternalStylesheetLoadingDisabled() {
            return withSafeAttribute(ACCESS_EXTERNAL_STYLESHEET, "");
        }

        /**
         * Configures the factory to not enable extension functions.
         *
         * <p>Uses the {@code jdk.xml.enableExtensionFunctions}
         * attribute if the factory supports it and enables secure
         * processing otherwise - keeping the external access settings
         * in effect at that point.</p>
         * @return this
         */
        public Builder withExtensionFunctionsDisabled() {
            extensionFunctionsDisabled = true;
            return this;
        }

        /**
         * Configures the factory to enable secure processing which disables all external access as well as execution of
         * extension functions.
         * @return this
         */
        public Builder withSecureProcessingEnabled() {
            return withFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        }

    }
}
