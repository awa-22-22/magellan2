// class magellan.client.utils.RendererLoaderTest
// created on Aug 21, 2026
//
// Copyright 2003-2026 by magellan project team
//
// Author : $Author: $
// $Id: $
//
// This program is free software; you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation; either version 2 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program (see doc/LICENCE.txt); if not, write to the
// Free Software Foundation, Inc.,
// 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
//
package magellan.client.utils;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import magellan.client.swing.map.CellGeometry;
import magellan.client.swing.map.MapCellRenderer;
import magellan.test.MagellanTestUtil;

public class RendererLoaderTest {

  private Path testDir;

  @Before
  public void setUp() throws Exception {
    testDir = Path.of("test/RendererLoader");
    MagellanTestUtil.forceDelete(testDir);
    Files.createDirectories(testDir);
  }

  @After
  public void tearDown() throws Exception {
    MagellanTestUtil.forceDelete(testDir);
  }

  @Test
  public void testEmptyDirectory() throws Exception {
    RendererLoader loader =
        new RendererLoader(testDir.toFile(), ".", new CellGeometry(), new Properties());
    Collection<MapCellRenderer> renderers = loader.loadRenderers();
    assertTrue(renderers == null || renderers.isEmpty());
  }

  /**
   * A class file that cannot be linked (here: garbage bytes) must be skipped, not crash the
   * client. Regression test for the IncompatibleClassChangeError caused by
   * com.jgoodies.looks.windows.WindowsMenuItemRenderer of skinsCollected.jar, which is
   * incompatible with the looks-1.3b1 classes.
   */
  @Test
  public void testBrokenRendererClassIsSkipped() throws Exception {
    File jar = testDir.resolve("broken.jar").toFile();
    ZipOutputStream out = new ZipOutputStream(new FileOutputStream(jar));
    out.putNextEntry(new ZipEntry("fake/FakeRenderer.class"));
    out.write("this is not a class file".getBytes("ISO-8859-1"));
    out.closeEntry();
    out.close();

    RendererLoader loader =
        new RendererLoader(testDir.toFile(), ".", new CellGeometry(), new Properties());
    Collection<MapCellRenderer> renderers = loader.loadRenderers();
    assertTrue(renderers == null || renderers.isEmpty());
  }
}
