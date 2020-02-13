/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.operator.cluster;

import java.util.stream.Stream;

import javax.enterprise.context.ApplicationScoped;

import io.fabric8.kubernetes.api.model.EnvVar;
import io.stackgres.operator.common.StackGresClusterContext;
import io.stackgres.operatorframework.resource.factory.SubResourceStreamFactory;

import org.jooq.lambda.Seq;

@ApplicationScoped
public class ClusterStatefulSetEnvironmentVariables
    implements SubResourceStreamFactory<EnvVar, StackGresClusterContext> {

  public Stream<EnvVar> streamResources(StackGresClusterContext context) {
    return Seq.of(ClusterStatefulSetPath.values())
        .map(ClusterStatefulSetPath::envVar);
  }

}
