package net.sourceforge.opencamera;

import org.junit.experimental.categories.Categories;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;

/** Tests related to taking photos; note that tests to do with photo mode that don't take photos are still part of MainInstrumentedTests.
 */

@RunWith(Categories.class)
@Categories.IncludeCategory(PhotoCamera2Tests.class)
@Suite.SuiteClasses({InstrumentedTest.class})
public class PhotoCamera2InstrumentedTests {}
