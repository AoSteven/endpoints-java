package com.google.api.server.spi.config.model;

import static com.google.api.server.spi.config.model.EndpointsFlag.MAP_SCHEMA_FORCE_JSON_MAP_SCHEMA;
import static com.google.api.server.spi.config.model.EndpointsFlag.MAP_SCHEMA_IGNORE_UNSUPPORTED_KEY_TYPES;
import static com.google.api.server.spi.config.model.EndpointsFlag.MAP_SCHEMA_SUPPORT_ARRAYS_VALUES;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import com.google.api.server.spi.EndpointMethod;
import com.google.api.server.spi.ServiceContext;
import com.google.api.server.spi.TypeLoader;
import com.google.api.server.spi.config.AnnotationBoolean;
import com.google.api.server.spi.config.Api;
import com.google.api.server.spi.config.ApiConfigLoader;
import com.google.api.server.spi.config.ApiResourceProperty;
import com.google.api.server.spi.config.Transformer;
import com.google.api.server.spi.config.annotationreader.ApiConfigAnnotationReader;
import com.google.api.server.spi.config.model.ApiParameterConfig.Classification;
import com.google.api.server.spi.config.model.Schema.Field;
import com.google.api.server.spi.config.model.Schema.SchemaReference;
import com.google.api.server.spi.response.CollectionResponse;
import com.google.api.server.spi.testing.EnumEndpoint;
import com.google.api.server.spi.testing.EnumValue;
import com.google.api.server.spi.testing.TestEnum;
import com.google.common.reflect.TypeParameter;
import com.google.common.reflect.TypeToken;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Tests for {@link SchemaRepository}.
 */
public class SchemaRepositoryTest {
  private SchemaRepository repo;
  private ApiConfigLoader configLoader;
  private ApiConfig config;

  @Before
  public void setUp() throws Exception {
    TypeLoader typeLoader = new TypeLoader(getClass().getClassLoader());
    ApiConfigAnnotationReader annotationReader =
        new ApiConfigAnnotationReader(typeLoader.getAnnotationTypes());
    this.repo = new SchemaRepository(typeLoader);
    this.configLoader = new ApiConfigLoader(new ApiConfig.Factory(), typeLoader,
        annotationReader);
    this.config = configLoader.loadConfiguration(ServiceContext.create(), FooEndpoint.class);
  }

  @Test
  public void getOrAdd_genericRequestType() throws Exception {
    ApiMethodConfig methodConfig = fooEndpointSetParameterized();
    checkParameterizedSchema(
        repo.getOrAdd(getRequestResource(methodConfig).getType(), config), Integer.class);
  }

  @Test
  public void getOrAdd_genericReturnType() throws Exception {
    ApiMethodConfig methodConfig = fooEndpointSetParameterized();
    checkParameterizedSchema(
        repo.getOrAdd(methodConfig.getReturnType(), config), Integer.class);
  }

  @Test
  public void getOrAdd_responseCollection() throws Exception {
    ApiMethodConfig methodConfig = getMethodConfig("getIntegerCollection");
    checkIntegerCollectionResponse(repo.getOrAdd(methodConfig.getReturnType(), config));
  }

  @Test
  public void getOrAdd_intArray() throws Exception {
    ApiMethodConfig methodConfig = getMethodConfig("getPrimitiveIntegerArray");
    checkIntegerCollection(repo.getOrAdd(methodConfig.getReturnType(), config));
  }

  @Test
  public void getOrAdd_any() throws Exception {
    ApiMethodConfig methodConfig = getMethodConfig("getAny");
    assertThat(repo.getOrAdd(methodConfig.getReturnType(), config))
        .isEqualTo(SchemaRepository.ANY_SCHEMA);
  }

  @Test
  public void getOrAdd_mapType() throws Exception {
    //unsupported map types still use JsonMap schema
    checkJsonMap("getStringArrayMap");
    //non-string key values generate an exception
    try {
      checkJsonMap("getArrayStringMap");
      fail("Should have failed to generate map schema");
    } catch (IllegalArgumentException e) {
      //expected exception
    }
    //supported map types generate proper map schema
    ApiMethodConfig methodConfig = getMethodConfig("getStringEnumMap");
    Schema schema = repo.getOrAdd(methodConfig.getReturnType(), config);
    assertThat(schema).isEqualTo(Schema.builder()
        .setName("Map_String_TestEnum")
        .setDescription("A collection of name / TestEnum pairs")
        .setType("object")
        .setMapValueSchema(Field.builder()
            .setName(SchemaRepository.MAP_UNUSED_MSG)
            .setType(FieldType.ENUM)
            .setSchemaReference(SchemaReference.create(repo, config,
                TypeToken.of(TestEnum.class)))
            .build())
        .build());
  }

  @Test
  public void getOrAdd_mapSubType() throws Exception {
    Schema expectedSchema = Schema.builder()
        .setName("Map_String_String")
        .setType("object")
        .setMapValueSchema(Field.builder()
            .setName(SchemaRepository.MAP_UNUSED_MSG)
            .setType(FieldType.STRING)
            .build())
        .build();
    assertThat(repo.getOrAdd(getMethodConfig("getMyMap").getReturnType(), config))
        .isEqualTo(expectedSchema);
    assertThat(repo.getOrAdd(getMethodConfig("getMySubMap").getReturnType(), config))
        .isEqualTo(expectedSchema);
  }

  @Test
  public void getOrAdd_mapTypeUnsupportedKeys() throws Exception {
    System.setProperty(MAP_SCHEMA_IGNORE_UNSUPPORTED_KEY_TYPES.systemPropertyName, "true");
    try {
      checkJsonMap("getArrayStringMap");
    } finally {
      System.clearProperty(MAP_SCHEMA_IGNORE_UNSUPPORTED_KEY_TYPES.systemPropertyName);
    }
  }

  @Test
  public void getOrAdd_NestedMap() throws Exception {
    Schema expectedSchema = Schema.builder()
        .setName("Map_String_Map_String_String")
        .setType("object")
        .setDescription("A collection of name / Map_String_String pairs")
        .setMapValueSchema(Field.builder()
            .setName(SchemaRepository.MAP_UNUSED_MSG)
            .setType(FieldType.OBJECT)
            .setSchemaReference(SchemaReference.create(repo, config,
                new TypeToken<Map<String, String>>() {}))
            .build())
        .build();
    assertThat(repo.getOrAdd(getMethodConfig("getNestedMap").getReturnType(), config))
        .isEqualTo(expectedSchema);
  }

  @Test
  public void getOrAdd_ParameterizedMap() throws Exception {
    checkJsonMap("getParameterizedMap");
    checkJsonMap("getParameterizedKeyMap");
    checkJsonMap("getParameterizedValueMap");
  }

  @Test
  public void getOrAdd_RawMap() throws Exception {
    checkJsonMap("getRawMap");
  }

  @Test
  public void getOrAdd_mapTypeArrayValues() throws Exception {
    System.setProperty(MAP_SCHEMA_SUPPORT_ARRAYS_VALUES.systemPropertyName, "true");
    try {
      ApiMethodConfig methodConfig = getMethodConfig("getStringArrayMap");
      Schema schema = repo.getOrAdd(methodConfig.getReturnType(), config);
      assertThat(schema).isEqualTo(Schema.builder()
          .setName("Map_String_StringCollection")
          .setType("object")
          .setMapValueSchema(Field.builder()
              .setName(SchemaRepository.MAP_UNUSED_MSG)
              .setType(FieldType.ARRAY)
              .setArrayItemSchema(Field.builder()
                  .setName(SchemaRepository.ARRAY_UNUSED_MSG)
                  .setType(FieldType.STRING)
                  .build())
              .build())
          .build());
    } finally {
      System.clearProperty(MAP_SCHEMA_SUPPORT_ARRAYS_VALUES.systemPropertyName);
    }
  }

  @Test
  public void getOrAdd_jsonMap() throws Exception {
    System.setProperty(MAP_SCHEMA_FORCE_JSON_MAP_SCHEMA.systemPropertyName, "true");
    try {
      checkJsonMap("getStringEnumMap");
      checkJsonMap("getStringArrayMap");
      checkJsonMap("getArrayStringMap");
    } finally {
      System.clearProperty(MAP_SCHEMA_FORCE_JSON_MAP_SCHEMA.systemPropertyName);
    }
  }

  private void checkJsonMap(String methodName) throws Exception {
    ApiMethodConfig methodConfig = getMethodConfig(methodName);
    assertThat(repo.getOrAdd(methodConfig.getReturnType(), config))
        .isEqualTo(SchemaRepository.MAP_SCHEMA);
  }

  @Test
  public void getOrAdd_transformer() throws Exception {
    ApiMethodConfig methodConfig = getMethodConfig("getTransformed");
    checkParameterizedSchema(
        repo.getOrAdd(methodConfig.getReturnType(), config), String.class);
  }

  @Test(expected = IllegalArgumentException.class)
  public void getOrAdd_primitiveReturn() throws Exception {
    repo.getOrAdd(TypeToken.of(int.class), config);
  }

  @Test
  public void getOrAdd_enum() throws Exception {
    assertThat(repo.getOrAdd(TypeToken.of(TestEnum.class), config))
        .isEqualTo(Schema.builder()
            .setName("TestEnum")
            .setType("string")
            .addEnumValue("VALUE1")
            .addEnumValue("value_2")
            .addEnumDescription("")
            .addEnumDescription("")
            .build());
  }

  @Test
  public void getOrAdd_enum_disableJacksonAnnotations() throws Exception {
    System.setProperty(EndpointsFlag.JSON_USE_JACKSON_ANNOTATIONS.systemPropertyName, "false");
    try {
      assertThat(repo.getOrAdd(TypeToken.of(TestEnum.class), config))
          .isEqualTo(Schema.builder()
              .setName("TestEnum")
              .setType("string")
              .addEnumValue("VALUE1")
              .addEnumValue("VALUE2")
              .addEnumDescription("")
              .addEnumDescription("")
              .build());
    } finally {
      System.clearProperty(EndpointsFlag.JSON_USE_JACKSON_ANNOTATIONS.systemPropertyName);
    }
  }
  
  @Test
  public void getOrAdd_optional_foo() throws Exception {
    checkRequiredProperties(new TypeToken<Optional<RequiredProperties>>() {});
  }
  
  @Test
  public void getOrAdd_optional_enum() throws Exception {
    assertThat(repo.getOrAdd(new TypeToken<Optional<TestEnum>>() {}, config))
            .isEqualTo(Schema.builder()
                    .setName("TestEnum")
                    .setType("string")
                    .addEnumValue("VALUE1")
                    .addEnumValue("value_2")
                    .addEnumDescription("")
                    .addEnumDescription("")
                    .build());
  }
  
  @Test(expected = IllegalArgumentException.class)
  public void getOrAdd_optional_list() throws Exception {
    repo.getOrAdd(new TypeToken<Optional<List<String>>>() {}, config);
  }
  
  @Test(expected = IllegalArgumentException.class)
  public void getOrAdd_optional_map() throws Exception {
    repo.getOrAdd(new TypeToken<Optional<Map<String, String>>>() {}, config);

  }
  
  @Test(expected = IllegalArgumentException.class)
  public void getOrAdd_optional_object() throws Exception {
    repo.getOrAdd(new TypeToken<Optional<Object>>() {}, config);
  }
  
  @Test(expected = IllegalArgumentException.class)
  public void getOrAdd_optional_optional() throws Exception {
    repo.getOrAdd(new TypeToken<Optional<Optional<String>>>() {}, config);
  }

  @Test
  public void getOrAdd_recursiveSchema() throws Exception {
    TypeToken<SelfReferencingObject> type = TypeToken.of(SelfReferencingObject.class);
    // This test checks the case where a schema is added multiple times. The second time it's added
    // to an API, it recurses through the schema. If the schema is self-referencing, we must not
    // stack overflow.
    repo.getOrAdd(type, config);
    assertThat(repo.getOrAdd(type, config))
        .isEqualTo(Schema.builder()
            .setName("SelfReferencingObject")
            .setType("object")
            .addField("foo", Field.builder()
                .setName("foo")
                .setType(FieldType.OBJECT)
                .setSchemaReference(SchemaReference.create(repo, config, type))
                .build())
            .build());
  }

  @Test
  public void getOrAdd_requiredProperties() throws Exception {
    TypeToken<RequiredProperties> type = TypeToken.of(RequiredProperties.class);
    // This test checks the combinations of annotation that determine the "required" marker for
    // resource properties.
    checkRequiredProperties(type);
  }
  
  @Test
  public void get() {
    TypeToken<Parameterized<Integer>> type = new TypeToken<Parameterized<Integer>>() {};
    assertThat(repo.get(type, config)).isNull();
    repo.getOrAdd(type, config);
    checkParameterizedSchema(repo.get(type, config), Integer.class);
  }

  @Test
  public void getOrAdd_multipleApis() throws Exception {
    // Adding the same resource to multiple APIs should add field resources as well.
    ApiConfig config2 = configLoader.loadConfiguration(ServiceContext.create(), EnumEndpoint.class);
    repo.getOrAdd(new TypeToken<EnumValue>() {}, config);
    repo.getOrAdd(new TypeToken<EnumValue>() {}, config2);
    assertThat(repo.getAllSchemaForApi(config2.getApiKey().withoutRoot())).containsExactly(
        Schema.builder()
            .setName("TestEnum")
            .setType("string")
            .addEnumValue("VALUE1")
            .addEnumValue("value_2")
            .addEnumDescription("")
            .addEnumDescription("")
            .build(),
        Schema.builder()
            .setName("EnumValue")
            .setType("object")
            .addField("value", Field.builder()
                .setName("value")
                .setSchemaReference(SchemaReference.create(repo, config2, new TypeToken<TestEnum>() {}))
                .setType(FieldType.ENUM)
                .build())
            .build());
  }

  @Api(transformers = {ParameterizedShortTransformer.class})
  private static class FooEndpoint {
    public Parameterized<Integer> setParameterized(Parameterized<Integer> p) {
      return null;
    }

    public CollectionResponse<Integer> getIntegerCollection() {
      return null;
    }

    public int[] getPrimitiveIntegerArray() {
      return null;
    }

    public Object getAny() {
      return null;
    }

    public Map<String, TestEnum> getStringEnumMap() {
      return null;
    }

    public Map<String, String[]> getStringArrayMap() {
      return null;
    }

    public Map<String[], String> getArrayStringMap() {
      return null;
    }

    public MyMap getMyMap() {
      return null;
    }

    public Map<String, Map<String, String>> getNestedMap() {
      return null;
    }

    public <K, V> Map<K, V> getParameterizedMap() {
      return null;
    }

    public <K> Map<K, String> getParameterizedKeyMap() {
      return null;
    }

    public <V> Map<String, V> getParameterizedValueMap() {
      return null;
    }

    public Map getRawMap() {
      return null;
    }

    public MySubMap getMySubMap() {
      return null;
    }

    public Parameterized<Short> getTransformed() {
      return null;
    }
  }

  private static class MyMap extends HashMap<String, String> { }

  private static class MySubMap extends MyMap { }

  private static class Parameterized<T> {
    public T getFoo() {
      return null;
    }

    public void setFoo(T foo) { }

    public Parameterized<T> getNext() {
      return null;
    }

    public TestEnum getTestEnum() {
      return null;
    }
  }

  private static class ParameterizedShortTransformer implements
      Transformer<Parameterized<Short>, Parameterized<String>> {
    @Override
    public Parameterized<String> transformTo(Parameterized<Short> in) {
      return null;
    }

    @Override
    public Parameterized<Short> transformFrom(Parameterized<String> in) {
      return null;
    }
  }

  private static class RequiredProperties {
    public String getUndefined() {
      return null;
    }
    @ApiResourceProperty
    public String apiResourceProperty_undefined() {
      return null;
    }
    @ApiResourceProperty(required = AnnotationBoolean.TRUE)
    public String apiResourceProperty_required() {
      return "";
    }
    @ApiResourceProperty(required = AnnotationBoolean.FALSE)
    public String apiResourceProperty_not_required() {
      return null;
    }
    @Nullable
    public String getNullable() {
      return null;
    }
    @Nonnull
    public String getNonnull() {
      return "";
    }
    @ApiResourceProperty(required = AnnotationBoolean.TRUE) @Nullable
    public String getPriority1() {
      return "";
    }
    @Nonnull @Nullable
    public String getPriority2() {
      return "";
    }
    @ApiResourceProperty(required = AnnotationBoolean.FALSE) @Nonnull
    public String getPriority3() {
      return null;
    }
  }

  private static class SelfReferencingObject {
    public SelfReferencingObject getFoo() {
      return null;
    }
  }

  private ApiMethodConfig fooEndpointSetParameterized() throws Exception {
    return getMethodConfig("setParameterized", Parameterized.class);
  }

  private ApiMethodConfig getMethodConfig(String name, Class<?>... params) throws Exception {
    return config.getApiClassConfig().getMethods().get(
        EndpointMethod.create(FooEndpoint.class, FooEndpoint.class.getMethod(name, params)));
  }

  private static ApiParameterConfig getRequestResource(ApiMethodConfig methodConfig) {
    for (ApiParameterConfig parameterConfig : methodConfig.getParameterConfigs()) {
      if (parameterConfig.getClassification() == Classification.RESOURCE) {
        return parameterConfig;
      }
    }
    throw new IllegalStateException("no resource on method");
  }

  private <T> void checkParameterizedSchema(Schema schema, Class<T> type) {
    assertThat(schema).isEqualTo(Schema.builder()
        .setName("Parameterized_" + type.getSimpleName())
        .setType("object")
        .addField("foo", Field.builder()
            .setName("foo")
            .setType(FieldType.fromType(TypeToken.of(type)))
            .build())
        .addField("next", Field.builder()
            .setName("next")
            .setType(FieldType.OBJECT)
            .setSchemaReference(
                SchemaReference.create(repo, config, new TypeToken<Parameterized<T>>() {}
                    .where(new TypeParameter<T>() {}, TypeToken.of(type))))
            .build())
        .addField("testEnum", Field.builder()
            .setName("testEnum")
            .setType(FieldType.ENUM)
            .setSchemaReference(SchemaReference.create(repo, config, TypeToken.of(TestEnum.class)))
            .build())
        .build());
  }

  private static void checkIntegerCollectionResponse(Schema schema) {
    assertThat(schema).isEqualTo(Schema.builder()
        .setName("CollectionResponse_Integer")
        .setType("object")
        .addField("items", Field.builder()
            .setName("items")
            .setType(FieldType.ARRAY)
            .setArrayItemSchema(Field.builder()
                .setName("unused for array items")
                .setType(FieldType.INT32)
                .build())
            .build())
        .addField("nextPageToken", Field.builder()
            .setName("nextPageToken")
            .setType(FieldType.STRING)
            .build())
        .build());
  }

  private static void checkIntegerCollection(Schema schema) {
    assertThat(schema).isEqualTo(Schema.builder()
        .setName("IntegerCollection")
        .setType("object")
        .addField("items", Field.builder()
            .setName("items")
            .setType(FieldType.ARRAY)
            .setArrayItemSchema(Field.builder()
                .setName(SchemaRepository.ARRAY_UNUSED_MSG)
                .setType(FieldType.INT32)
                .build())
            .build())
        .build());
  }
  
  private void checkRequiredProperties(TypeToken<?> type) {
    assertThat(repo.getOrAdd(type, config))
            .isEqualTo(Schema.builder()
                    .setName("RequiredProperties")
                    .setType("object")
                    .addField("undefined", Field.builder()
                            .setName("undefined")
                            .setType(FieldType.STRING)
                            .build())
                    .addField("apiResourceProperty_undefined", Field.builder()
                            .setName("apiResourceProperty_undefined")
                            .setType(FieldType.STRING)
                            .build())
                    .addField("apiResourceProperty_required", Field.builder()
                            .setName("apiResourceProperty_required")
                            .setRequired(true)
                            .setType(FieldType.STRING)
                            .build())
                    .addField("apiResourceProperty_not_required", Field.builder()
                            .setName("apiResourceProperty_not_required")
                            .setRequired(false)
                            .setType(FieldType.STRING)
                            .build())
                    .addField("nullable", Field.builder()
                            .setName("nullable")
                            .setRequired(false)
                            .setType(FieldType.STRING)
                            .build())
                    .addField("nonnull", Field.builder()
                            .setName("nonnull")
                            .setRequired(true)
                            .setType(FieldType.STRING)
                            .build())
                    .addField("priority1", Field.builder()
                            .setName("priority1")
                            .setRequired(true)
                            .setType(FieldType.STRING)
                            .build())
                    .addField("priority2", Field.builder()
                            .setName("priority2")
                            .setRequired(true)
                            .setType(FieldType.STRING)
                            .build())
                    .addField("priority3", Field.builder()
                            .setName("priority3")
                            .setRequired(false)
                            .setType(FieldType.STRING)
                            .build())
                    .build());
  }
}
