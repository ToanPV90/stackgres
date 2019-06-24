/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.resource;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.ConfigMapList;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.stackgres.app.KubernetesClientFactory;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class SgConfigMaps {

  private static final Logger LOGGER = LoggerFactory.getLogger(SgConfigMaps.class);

  @ConfigProperty(name = "stackgres.namespace", defaultValue = "stackgres")
  @NonNull
  String namespace;

  @Inject
  @NonNull
  KubernetesClientFactory kubClientFactory;

  /**
   * Create the Service associated to the cluster.
   */
  public @NonNull ConfigMap create(@NonNull String configMapName) {
    LOGGER.debug("Creating service name: {}", configMapName);

    Map<String, String> labels = new HashMap<>();
    labels.put("app", "stackgres");
    labels.put("stackgres-cluster", configMapName);

    String patroniLabels = labels.entrySet().stream()
        .map(f -> f.getKey() + ": \"" + f.getValue() + "\"")
        .collect(Collectors.joining(", ", "{", "}"));

    Map<String, String> data = new HashMap<>();
    data.put("PATRONI_SCOPE", configMapName);
    data.put("PATRONI_KUBERNETES_NAMESPACE", namespace);
    data.put("DCS_ENABLE_KUBERNETES_API", "true");
    data.put("PATRONI_SUPERUSER_USERNAME", "postgres");
    data.put("PATRONI_REPLICATION_USERNAME", "replication");
    data.put("PATRONI_KUBERNETES_USE_ENDPOINTS", "true");
    data.put("PATRONI_KUBERNETES_LABELS", patroniLabels);
    data.put("PATRONI_POSTGRESQL_LISTEN", "0.0.0.0");
    data.put("PATRONI_RESTAPI_LISTEN", "0.0.0.0:8008");
    data.put("PATRONI_POSTGRESQL_DATA_DIR", "/var/lib/postgresql/data");
    data.put("PATRONI_POSTGRESQL_BIN_DIR", "/usr/lib/postgresql/11/bin");
    data.put("PATRONI_CONFIG_DIR", "/var/lib/postgresql/data");
    data.put("PATRONI_POSTGRESQL_PORT", "5432");
    data.put("PATRONI_POSTGRES_UNIX_SOCKET_DIRECTORY", "/var/run/postgresql");
    data.put("PATRONI_LOG_LEVEL", "DEBUG");

    try (KubernetesClient client = kubClientFactory.retrieveKubernetesClient()) {
      ConfigMap cm = new ConfigMapBuilder()
          .withNewMetadata()
          .withName(configMapName)
          .endMetadata()
          .withData(data)
          .build();

      LOGGER.debug("Creating config map: {}", configMapName);

      client.configMaps().inNamespace(namespace).createOrReplace(cm);

      ConfigMapList list = client.configMaps().inNamespace(namespace).list();
      for (ConfigMap item : list.getItems()) {
        LOGGER.debug(item.getMetadata().getName());
        if (item.getMetadata().getName().equals(configMapName)) {
          cm = item;
        }
      }

      return cm;
    }
  }

}