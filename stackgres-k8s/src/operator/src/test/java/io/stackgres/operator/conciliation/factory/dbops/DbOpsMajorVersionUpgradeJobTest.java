/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.operator.conciliation.factory.dbops;

import io.quarkus.test.junit.QuarkusTest;
import io.stackgres.common.crd.sgdbops.StackGresDbOps;
import io.stackgres.common.fixture.Fixtures;

@QuarkusTest
class DbOpsMajorVersionUpgradeJobTest extends DbOpsJobTestCase {

  @Override
  StackGresDbOps getDbOps() {
    return Fixtures.dbOps().loadMajorVersionUpgrade().get();
  }

}