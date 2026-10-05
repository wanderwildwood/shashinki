package net.sourceforge.opencamera;

import org.junit.experimental.categories.Categories;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;

/** Tests to run specifically on Nexus 7, or similar emulator, including testing for devices with LEGACY Camera2 functionality.
 */

@RunWith(Categories.class)
@Categories.IncludeCategory(Nexus7Tests.class)
@Suite.SuiteClasses({InstrumentedTest.class})
public class Nexus7InstrumentedTests {}
