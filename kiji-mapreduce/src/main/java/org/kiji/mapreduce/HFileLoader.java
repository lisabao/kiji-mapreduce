/**
 * (c) Copyright 2012 WibiData, Inc.
 *
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.kiji.mapreduce;

import java.io.IOException;
import java.util.List;

import com.google.common.collect.Lists;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hbase.TableNotFoundException;
import org.apache.hadoop.hbase.client.HTable;
import org.apache.hadoop.hbase.mapreduce.LoadIncrementalHFiles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.kiji.annotations.ApiAudience;
import org.kiji.schema.InternalKijiError;
import org.kiji.schema.KijiTable;
import org.kiji.schema.impl.HBaseKijiTable;

/**
 * Loads HFiles generated by a MapReduce job into a Kiji table.
 *
 * @see org.kiji.mapreduce.output.HFileMapReduceJobOutput
 * @see org.kiji.mapreduce.tools.KijiBulkLoad
 */
@ApiAudience.Public
public final class HFileLoader {
  private static final Logger LOG = LoggerFactory.getLogger(HFileLoader.class);

  private final Configuration mConf;

  /**
   * Creates a new <code>HFileLoader</code> instance.
   *
   * @param conf The Hadoop configuration.
   */
  private HFileLoader(Configuration conf) {
    mConf = conf;
  }

  /**
   * Creates a new HFile loader.
   *
   * @param conf The configuration to be used by the new loader.
   * @return A new loader that can be used to add HFiles to HBase tables.
   */
  public static HFileLoader create(Configuration conf) {
    return new HFileLoader(conf);
  }

  /**
   * Loads partitioned HFiles directly into the regions of a Kiji table.
   *
   * @param hfilePath The path to the HFiles generated by a bulk-import job.
   * @param table The target kiji table.
   * @throws IOException If there is an error.
   */
  public void load(Path hfilePath, KijiTable table) throws IOException {
    LoadIncrementalHFiles loader;
    try {
      loader = new LoadIncrementalHFiles(mConf); // throws Exception
    } catch (Exception exn) {
      throw new InternalKijiError(exn);
    }

    try {
      // doBulkLoad() requires a concrete HTable instance:
      final HTable htable = (HTable) HBaseKijiTable.downcast(table).getHTable();

      List<Path> hfilePaths = Lists.newArrayList();

      // Try to find any hfiles for partitions within the passed in path
      final FileStatus[] hfiles = FileSystem.get(mConf).globStatus(new Path(hfilePath, "*"));
      for (FileStatus hfile : hfiles) {
        String partName = hfile.getPath().getName();
        if (!partName.startsWith("_") && partName.endsWith(".hfile")) {
          Path partHFile = new Path(hfilePath, partName);
          hfilePaths.add(partHFile);
        }
      }
      if (hfilePaths.isEmpty()) {
        // If we didn't find any parts, add in the passed in parameter
        hfilePaths.add(hfilePath);
      }
      for (Path path : hfilePaths) {
        loader.doBulkLoad(path, htable);
        LOG.info("Successfully loaded: " + path.toString());
      }
    } catch (TableNotFoundException tnfe) {
      throw new InternalKijiError(tnfe);
    }
  }
}
