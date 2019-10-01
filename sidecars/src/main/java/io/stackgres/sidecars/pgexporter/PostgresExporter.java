/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.sidecars.pgexporter;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.ContainerPortBuilder;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.EnvVarSourceBuilder;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.SecretKeySelectorBuilder;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.ServicePortBuilder;
import io.fabric8.kubernetes.api.model.ServiceSpecBuilder;
import io.fabric8.kubernetes.api.model.VolumeMount;
import io.fabric8.kubernetes.api.model.VolumeMountBuilder;
import io.stackgres.common.StackGresClusterConfig;
import io.stackgres.common.StackGresSidecarTransformer;
import io.stackgres.common.resource.ResourceUtil;
import io.stackgres.sidecars.pgexporter.customresources.ServiceMonitor;
import io.stackgres.sidecars.pgexporter.customresources.ServiceMonitorDefinition;
import io.stackgres.sidecars.pgexporter.customresources.StackGresPostgresExporterConfig;

public class PostgresExporter
    implements StackGresSidecarTransformer<StackGresPostgresExporterConfig> {

  private static final String NAME = "prometheus-postgres-exporter";
  private static final String IMAGE = "docker.io/ongres/prometheus-postgres-exporter:";
  private static final String DEFAULT_VERSION = "0.5.1";

  public PostgresExporter() {}

  @Override
  public Container getContainer(StackGresClusterConfig config) {
    Optional<StackGresPostgresExporterConfig> postgresExporterConfig =
        config.getSidecarConfig(this);
    VolumeMount pgSocket = new VolumeMountBuilder()
        .withName("pg-socket")
        .withMountPath("/run/postgresql")
        .build();

    ContainerBuilder container = new ContainerBuilder();
    container.withName(NAME)
        .withImage(IMAGE + postgresExporterConfig
            .map(c -> c.getSpec().getPostgresExporterVersion())
            .orElse(DEFAULT_VERSION))
        .withImagePullPolicy("Always")
        .withEnv(new EnvVarBuilder()
            .withName("DATA_SOURCE_NAME")
            .withValue("host=/var/run/postgresql user=postgres")
            .build(),
            new EnvVarBuilder()
            .withName("POSTGRES_EXPORTER_USERNAME")
            .withValue("postgres")
            .build(),
            new EnvVarBuilder()
            .withName("POSTGRES_EXPORTER_PASSWORD")
            .withValueFrom(new EnvVarSourceBuilder().withSecretKeyRef(
                new SecretKeySelectorBuilder()
                .withName(config.getCluster().getMetadata().getName())
                .withKey("superuser-password")
                .build())
                .build())
            .build())
        .withPorts(new ContainerPortBuilder()
            .withContainerPort(9187)
            .build())
        .withVolumeMounts(pgSocket);

    return container.build();
  }

  @Override
  public List<HasMetadata> getResources(StackGresClusterConfig config) {
    Map<String, String> labels = ResourceUtil.defaultLabels(
        config.getCluster().getMetadata().getName());
    Optional<StackGresPostgresExporterConfig> postgresExporterConfig =
        config.getSidecarConfig(this);
    ImmutableList.Builder<HasMetadata> resourcesBuilder = ImmutableList.builder();
    resourcesBuilder.add(
        new ServiceBuilder()
        .withNewMetadata()
        .withNamespace(config.getCluster().getMetadata().getNamespace())
        .withName(config.getCluster().getMetadata().getName() + "-" + NAME)
        .withLabels(ImmutableMap.<String, String>builder()
            .putAll(labels)
            .put("container", NAME)
            .build())
        .endMetadata()
        .withSpec(new ServiceSpecBuilder()
            .withSelector(labels)
            .withPorts(new ServicePortBuilder()
                .withName(NAME)
                .withPort(9187)
                .build())
            .build())
        .build());
    if (postgresExporterConfig
        .flatMap(c -> Optional.ofNullable(c.getSpec().getCreateServiceMonitor()))
        .orElse(false)) {
      ServiceMonitor serviceMonitor = new ServiceMonitor();
      serviceMonitor.setKind(ServiceMonitorDefinition.KIND);
      serviceMonitor.setApiVersion(ServiceMonitorDefinition.APIVERSION);
      serviceMonitor.setMetadata(new ObjectMetaBuilder()
          .withName("stackgres-prometheus-postgres-exporter")
          .withLabels(ImmutableMap.of("team", "stackgres-prometheus-postgres-exporter"))
          .build());
      resourcesBuilder.add(serviceMonitor);
    }
    return resourcesBuilder.build();
  }

}
