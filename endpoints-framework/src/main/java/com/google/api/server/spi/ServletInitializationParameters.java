/*
 * Copyright 2016 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.api.server.spi;

import com.google.auto.value.AutoValue;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;

import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;

/**
 * Initialization parameters supported by the {@link EndpointsServlet}.
 */
@AutoValue
public abstract class ServletInitializationParameters {
  // Initialization parameter names used to extract values from a ServletConfig.
  private static final String SERVICES = "services";
  private static final String RESTRICTED = "restricted";
  private static final String CLIENT_ID_WHITELIST_ENABLED = "clientIdWhitelistEnabled";
  private static final String ILLEGAL_ARGUMENT_BACKEND_ERROR = "illegalArgumentIsBackendError";
  private static final String EXCEPTION_COMPATIBILITY = "enableExceptionCompatibility";
  private static final String PRETTY_PRINT = "prettyPrint";
  private static final String ADD_CONTENT_LENGTH = "addContentLength";
  private static final String API_EXPLORER_URL_TEMPLATE = "apiExplorerUrlTemplate";
  private static final String PARAMETER_VALIDATION = "enableValidation";
  private static final String CONTENT_TYPE_VALIDATION = "enableContentTypeValidation";

  private static final Splitter CSV_SPLITTER = Splitter.on(',').omitEmptyStrings().trimResults();
  private static final Joiner CSV_JOINER = Joiner.on(',').skipNulls();
  private static final Function<Class<?>, String> CLASS_TO_NAME = new Function<Class<?>, String>() {
    @Override
    public String apply(Class<?> clazz) {
      return clazz.getName();
    }
  };

  /**
   * Returns the endpoint service classes to serve.
   */
  public abstract ImmutableSet<Class<?>> getServiceClasses();

  /**
   * Returns if client ID whitelisting is enabled.
   */
  public abstract boolean isClientIdWhitelistEnabled();

  /**
   * Returns if an {@link IllegalArgumentException} should be returned as a backend error (500
   * level) instead of a user error (400 level).
   */
  public abstract boolean isIllegalArgumentBackendError();

  /**
   * Returns if v1.0 style exceptions should be returned to users. In v1.0, certain codes are not
   * permissible, and other codes are translated to other status codes.
   */
  public abstract boolean isExceptionCompatibilityEnabled();

  /**
   * Returns if pretty printing should be enabled for responses by default. Defaults to true.
   */
  public abstract boolean isPrettyPrintEnabled();

  /**
   * Returns if the Content-Length header should be set on response. Should be disabled when running
   * on App Engine, as Content-Length header is discarded by front-end servers. If enabled, has a
   * small negative impact on CPU usage and latency.
   *
   */
  public abstract boolean isAddContentLength();

  /**
   * Returns whether the request parameter validation is enabled.
   */
  public abstract boolean isParameterValidationEnabled();

  /**
   * Returns whether the request content type validation is enabled.
   */
  public abstract boolean isContentTypeValidationEnabled();
  
  @Nullable
  public abstract String getApiExplorerUrlTemplate();

  public static Builder builder() {
    return new AutoValue_ServletInitializationParameters.Builder()
        .setClientIdWhitelistEnabled(true)
        .setIllegalArgumentBackendError(false)
        .setExceptionCompatibilityEnabled(true)
        .setPrettyPrintEnabled(true)
        .setAddContentLength(false)
        .setParameterValidationEnabled(true)
        .setContentTypeValidationEnabled(false)
        .setApiExplorerUrlTemplate(null);
  }

  /**
   * A builder for {@link ServletInitializationParameters}.
   */
  @AutoValue.Builder
  public abstract static class Builder {
    private final ImmutableSet.Builder<Class<?>> serviceClasses = ImmutableSet.builder();

    /**
     * Adds an endpoint service class to serve.
     */
    public Builder addServiceClass(Class<?> serviceClass) {
      this.serviceClasses.add(serviceClass);
      return this;
    }

    /**
     * Adds some endpoint service classes to serve.
     */
    public Builder addServiceClasses(Iterable<? extends Class<?>> serviceClasses) {
      this.serviceClasses.addAll(serviceClasses);
      return this;
    }

    /**
     * Sets the complete list of endpoint service classes to serve.
     */
    public abstract Builder setServiceClasses(ImmutableSet<Class<?>> clazzes);

    /**
     * Sets if the client ID whitelist is enabled, defaulting to {@code true}.
     */
    public abstract Builder setClientIdWhitelistEnabled(boolean clientIdWhitelist);

    /**
     * Sets if an {@link IllegalArgumentException} should be treated as a backend error (500)
     * instead of a user error (400). Defaults to {@code false}.
     */
    public abstract Builder setIllegalArgumentBackendError(boolean illegalArgumentBackendError);

    /**
     * Sets if request parameter validation should be enabled. Defaults to {@code true}.
     */
    public abstract Builder setParameterValidationEnabled(boolean enabledParameterValidation);
  
    /**
     * Sets if request content type validation should be enabled. Defaults to {@code false}.
     */
    public abstract Builder setContentTypeValidationEnabled(boolean enabledContentTypeValidation);

    /**
     * Sets if pretty printing should be enabled for responses by default. Defaults to {@code true}.
     */
    public abstract Builder setPrettyPrintEnabled(boolean prettyPrint);

    /**
     * Sets if the content length header should be set. Defaults to {@code false}.
     */
    public abstract Builder setAddContentLength(boolean addContentLength);

    /**
     * Sets the url template for the API explorer.
     * The only supported variable is ${apiBase}.
     * Given https://myapp.com/_ah/api/explorer, ${apiBase} is set to https://myapp.com/_ah/api.
     * Defaults to http://apis-explorer.appspot.com/apis-explorer/?base=${apiBase} if not set.
     */
    public abstract Builder setApiExplorerUrlTemplate(String urlTemplate);
  
    /**
     * Sets if v1.0 style exceptions should be returned to users. In v1.0, certain codes are not
     * permissible, and other codes are translated to other status codes. Defaults to {@code true}.
     */
    public abstract Builder setExceptionCompatibilityEnabled(boolean exceptionCompatibility);
    
    abstract ServletInitializationParameters autoBuild();

    public ServletInitializationParameters build() {
      return setServiceClasses(serviceClasses.build()).autoBuild();
    }
  }

  /**
   * Constructs a new instance from the provided {@link ServletConfig} and {@link ClassLoader}.
   */
  public static ServletInitializationParameters fromServletConfig(
      ServletConfig config, ClassLoader classLoader) throws ServletException {
    Builder builder = builder();
    if (config != null) {
      String serviceClassNames = config.getInitParameter(SERVICES);
      if (serviceClassNames != null) {
        for (String serviceClassName : CSV_SPLITTER.split(serviceClassNames)) {
          builder.addServiceClass(getClassForName(serviceClassName, classLoader));
        }
      }
      String clientIdWhitelist = config.getInitParameter(CLIENT_ID_WHITELIST_ENABLED);
      if (clientIdWhitelist != null) {
        builder.setClientIdWhitelistEnabled(
            parseBoolean(clientIdWhitelist, CLIENT_ID_WHITELIST_ENABLED));
      }
      String illegalArgumentBackendError = config.getInitParameter(ILLEGAL_ARGUMENT_BACKEND_ERROR);
      if (illegalArgumentBackendError != null) {
        builder.setIllegalArgumentBackendError(
            parseBoolean(illegalArgumentBackendError, ILLEGAL_ARGUMENT_BACKEND_ERROR));
      }
      String exceptionCompatibility = config.getInitParameter(EXCEPTION_COMPATIBILITY);
      if (exceptionCompatibility != null) {
        builder.setExceptionCompatibilityEnabled(
            parseBoolean(exceptionCompatibility, EXCEPTION_COMPATIBILITY));
      }
      String prettyPrint = config.getInitParameter(PRETTY_PRINT);
      if (prettyPrint != null) {
        builder.setPrettyPrintEnabled(parseBoolean(prettyPrint, PRETTY_PRINT));
      }
      String addContentLength = config.getInitParameter(ADD_CONTENT_LENGTH);
      if (addContentLength != null) {
        builder.setAddContentLength(parseBoolean(addContentLength, ADD_CONTENT_LENGTH));
      }
      String enabledParameterValidation = config.getInitParameter(PARAMETER_VALIDATION);
      if (enabledParameterValidation != null) {
        builder.setParameterValidationEnabled(
                parseBoolean(enabledParameterValidation, PARAMETER_VALIDATION));
      }
      String enabledContentTypeValidation = config.getInitParameter(CONTENT_TYPE_VALIDATION);
      if (enabledContentTypeValidation != null) {
        builder.setContentTypeValidationEnabled(
                parseBoolean(enabledContentTypeValidation, CONTENT_TYPE_VALIDATION));
      }
      builder.setApiExplorerUrlTemplate(config.getInitParameter(API_EXPLORER_URL_TEMPLATE));
    }
    return builder.build();
  }

  private static boolean parseBoolean(String booleanString, String descriptionForErrors) {
    if ("true".equalsIgnoreCase(booleanString)) {
      return true;
    } else if ("false".equalsIgnoreCase(booleanString)) {
      return false;
    }
    throw new IllegalArgumentException(String.format(
        "Expected 'true' or 'false' for '%s' servlet initialization parameter but got '%s'",
        descriptionForErrors, booleanString));
  }

  private static Class<?> getClassForName(String className, ClassLoader classLoader)
      throws ServletException {
    try {
      return Class.forName(className, true, classLoader);
    } catch (ClassNotFoundException e) {
      throw new ServletException(String.format("Cannot find service class: %s", className), e);
    }
  }

  /**
   * Returns the parameters as a {@link java.util.Map} of parameter name to {@link String} value.
   */
  public Map<String, String> asMap() {
    return new HashMap<String, String>() {{
          put(SERVICES, CSV_JOINER.join(Iterables.transform(getServiceClasses(), CLASS_TO_NAME)));
          put(CLIENT_ID_WHITELIST_ENABLED, Boolean.toString(isClientIdWhitelistEnabled()));
          put(ILLEGAL_ARGUMENT_BACKEND_ERROR, Boolean.toString(isIllegalArgumentBackendError()));
          put(EXCEPTION_COMPATIBILITY, Boolean.toString(isExceptionCompatibilityEnabled()));
          put(PRETTY_PRINT, Boolean.toString(isPrettyPrintEnabled()));
          put(ADD_CONTENT_LENGTH, Boolean.toString(isAddContentLength()));
          put(PARAMETER_VALIDATION, Boolean.toString(isParameterValidationEnabled()));
          put(CONTENT_TYPE_VALIDATION, Boolean.toString(isContentTypeValidationEnabled()));
          put(API_EXPLORER_URL_TEMPLATE, getApiExplorerUrlTemplate());
      }};
  }
}
