package io.stackgres.operator.utils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.runtime.annotations.RegisterForReflection;
import org.apache.commons.io.IOUtils;

public class JsonUtil {

  private static final ObjectMapper jsonMapper = new ObjectMapper();

  public static  <T> T readFromJson(String resource, Class<T> clazz){
    if (clazz.getPackage().getName().startsWith("io.stackgres")){
      if (clazz.getAnnotation(RegisterForReflection.class) == null){
        throw new NullPointerException("class " + clazz.getName() + " must have the annotation: " + RegisterForReflection.class);
      }
    }
    try (InputStream is = ClassLoader.getSystemResourceAsStream(resource)){
      if (is == null){
        throw new IllegalArgumentException("resource " + resource + " not found");
      }
      String json = IOUtils.toString(is, StandardCharsets.UTF_8);
      return jsonMapper.readValue(json, clazz);
    } catch (IOException e) {
      throw new IllegalArgumentException("could not open resource " + resource, e);
    }
  }

}