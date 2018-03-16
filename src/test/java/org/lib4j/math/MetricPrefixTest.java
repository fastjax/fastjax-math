/* Copyright (c) 2018 lib4j
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * You should have received a copy of The MIT License (MIT) along with this
 * program. If not, see <http://opensource.org/licenses/MIT/>.
 */

package org.lib4j.math;

import org.junit.Assert;
import org.junit.Test;

public class MetricPrefixTest {
  @Test
  public void test() {
    Assert.assertNull(MetricPrefix.ofPower(-27));
    Assert.assertEquals(MetricPrefix.YOCTO, MetricPrefix.ofPower(-24));
    Assert.assertEquals(MetricPrefix.ATTO, MetricPrefix.ofPower(-18));
    Assert.assertNull(MetricPrefix.ofPower(0));
    Assert.assertEquals(MetricPrefix.MEGA, MetricPrefix.ofPower(6));
    Assert.assertEquals(MetricPrefix.GIGA, MetricPrefix.ofPower(9));
    Assert.assertEquals(MetricPrefix.YOTTA, MetricPrefix.ofPower(24));
    Assert.assertNull(MetricPrefix.ofPower(27));
  }
}