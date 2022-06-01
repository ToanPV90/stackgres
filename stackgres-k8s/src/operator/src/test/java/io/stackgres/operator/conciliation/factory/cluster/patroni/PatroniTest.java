/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.operator.conciliation.factory.cluster.patroni;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.ResourceRequirements;
import io.fabric8.kubernetes.api.model.VolumeBuilder;
import io.stackgres.common.LabelFactoryForCluster;
import io.stackgres.common.StackGresComponent;
import io.stackgres.common.StackGresUtil;
import io.stackgres.common.crd.sgcluster.StackGresCluster;
import io.stackgres.operator.conciliation.cluster.StackGresClusterContext;
import io.stackgres.operator.conciliation.factory.ContainerContext;
import io.stackgres.operator.conciliation.factory.ImmutableVolumePair;
import io.stackgres.operator.conciliation.factory.PostgresContainerContext;
import io.stackgres.operator.conciliation.factory.ResourceFactory;
import io.stackgres.operator.conciliation.factory.VolumeDiscoverer;
import io.stackgres.operator.conciliation.factory.VolumeMountsProvider;
import io.stackgres.operator.conciliation.factory.cluster.StackGresClusterContainerContext;
import io.stackgres.operator.conciliation.factory.cluster.StatefulSetDynamicVolumes;
import io.stackgres.testutil.JsonUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PatroniTest {

  private static final String POSTGRES_VERSION =
      StackGresComponent.POSTGRESQL.getLatest().getOrderedVersions().findFirst().get();

  @Mock
  ResourceFactory<StackGresClusterContext, List<EnvVar>> patroniEnvironmentVariables;
  @Mock
  ResourceFactory<StackGresClusterContext, ResourceRequirements> requirementsFactory;
  @Mock
  LabelFactoryForCluster<StackGresCluster> labelFactory;
  @Mock
  VolumeMountsProvider<ContainerContext> postgresSocket;
  @Mock
  VolumeMountsProvider<PostgresContainerContext> postgresExtensions;
  @Mock
  VolumeMountsProvider<ContainerContext> localBinMounts;
  @Mock
  VolumeMountsProvider<ContainerContext> restoreMounts;
  @Mock
  VolumeMountsProvider<ContainerContext> backupMounts;
  @Mock
  VolumeMountsProvider<StackGresClusterContainerContext> hugePagesMounts;

  @Mock
  VolumeDiscoverer<StackGresClusterContext> volumeDiscoverer;

  private Patroni patroni;

  @Mock
  private StackGresClusterContainerContext clusterContainerContext;

  @Mock
  private StackGresClusterContext clusterContext;

  @Mock
  private ResourceRequirements podResources;

  private StackGresCluster cluster;

  @BeforeEach
  void setUp() {
    patroni = new Patroni(patroniEnvironmentVariables, requirementsFactory, labelFactory,
        postgresSocket, postgresExtensions, localBinMounts, restoreMounts, backupMounts,
        hugePagesMounts, volumeDiscoverer);
    cluster = JsonUtil.readFromJson("stackgres_cluster/default.json", StackGresCluster.class);
    cluster.getSpec().getPostgres().setVersion(POSTGRES_VERSION);
    when(clusterContainerContext.getClusterContext()).thenReturn(clusterContext);
    when(requirementsFactory.createResource(clusterContext)).thenReturn(podResources);
    when(clusterContainerContext.getDataVolumeName()).thenReturn("test");
    when(patroniEnvironmentVariables.createResource(clusterContext)).thenReturn(List.of());
    when(volumeDiscoverer.discoverVolumes(clusterContext))
        .thenReturn(Map.of(StatefulSetDynamicVolumes.PATRONI_ENV.getVolumeName(),
            ImmutableVolumePair.builder()
            .volume(new VolumeBuilder()
                .withNewConfigMap()
                .withName("test")
                .endConfigMap()
                .build())
            .source(new ConfigMapBuilder()
                .withData(Map.of(StackGresUtil.MD5SUM_KEY, "test"))
                .build())
            .build()));
  }

  @Test
  void givenACluster_itShouldGetHugePagesMountsAndEnvVars() {
    when(clusterContext.getSource()).thenReturn(cluster);
    when(clusterContext.getCluster()).thenReturn(cluster);

    patroni.getContainer(clusterContainerContext);

    verify(hugePagesMounts, times(1)).getVolumeMounts(any());
    verify(hugePagesMounts, times(1)).getDerivedEnvVars(any());
  }

}