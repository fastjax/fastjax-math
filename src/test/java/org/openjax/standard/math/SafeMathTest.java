/* Copyright (c) 2008 OpenJAX
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

package org.openjax.standard.math;

import static org.junit.Assert.*;

import org.junit.Ignore;
import org.junit.Test;
import org.openjax.standard.math.SafeMath;

public class SafeMathTest {
  @Test
  @Ignore("FIXME: Implement this")
  public void testNumberFunctions() {
  }

  @Test
  public void testRound() {
    try {
      assertEquals(12d, SafeMath.round(12.45, -1), 0);
    }
    catch (final IllegalArgumentException e) {
      if (!"scale < 0: -1".equals(e.getMessage()))
        throw e;
    }

    assertEquals(12d, SafeMath.round(12.45, 0), 0);
    assertEquals(12.5d, SafeMath.round(12.45, 1), 0);
    assertEquals(12.45d, SafeMath.round(12.45, 2), 0);
  }

  @Test
  public void testLog() {
    assertEquals(0d, SafeMath.log(0, 2), 0d);
    assertEquals(0d, SafeMath.log(2, 1), 0d);
    assertEquals(2d, SafeMath.log(2, 4), 0d);
    assertEquals(Double.POSITIVE_INFINITY, SafeMath.log(1, 2), 0d);
    assertEquals(Double.NEGATIVE_INFINITY, SafeMath.log(1, 0), 0d);
  }
}