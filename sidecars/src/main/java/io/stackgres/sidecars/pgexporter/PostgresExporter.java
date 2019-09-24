/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.sidecars.pgexporter;

import java.util.List;
import java.util.Map;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.ContainerPortBuilder;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.EnvVarSourceBuilder;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.SecretKeySelectorBuilder;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.ServicePortBuilder;
import io.fabric8.kubernetes.api.model.ServiceSpecBuilder;
import io.fabric8.kubernetes.api.model.VolumeMount;
import io.fabric8.kubernetes.api.model.VolumeMountBuilder;
import io.stackgres.common.ResourceUtils;
import io.stackgres.common.sgcluster.StackGresCluster;
import io.stackgres.sidecars.Sidecar;

public class PostgresExporter implements Sidecar {

  private static final String NAME = "prometheus-postgres-exporter";
  private static final String IMAGE = "docker.io/ongres/prometheus-postgres-exporter:0.5.1";

  public PostgresExporter() {}

  @Override
  public Container create(StackGresCluster resource) {
    VolumeMount pgSocket = new VolumeMountBuilder()
        .withName("pg-socket")
        .withMountPath("/run/postgresql")
        .build();

    ContainerBuilder container = new ContainerBuilder();
    container.withName(NAME)
        .withImage(IMAGE)
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
                .withName(resource.getMetadata().getName())
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
  public String getName() {
    return NAME;
  }

  @Override
  public List<HasMetadata> createDependencies(StackGresCluster resource) {
    Map<String, String> labels = ResourceUtils.defaultLabels(
        resource.getMetadata().getName());
    return ImmutableList.of(
        new ServiceBuilder()
        .withNewMetadata()
        .withName(NAME)
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
  }

}