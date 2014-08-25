package org.apache.spark.sql.hive
import org.apache.spark.{SparkConf, SparkContext}
/**
 * Created by animal on 4/4/14.
 */
object TestPanthera {
  def main(args: Array[String]) {
    val spark = new SparkContext("local", "TestSQLPantheraContext", new SparkConf())
    val lhive = (new LocalHiveContext(spark))
    lhive.sql(s"""drop database IF EXISTS hive CASCADE""")
    lhive.sql(s"""create database hive""")
    lhive.sql(s"""use hive""")
    lhive.sql(s"""CREATE TABLE SUPPLIER(S_SUPPKEY INT,S_NAME STRING,S_ADDRESS STRING,S_NATIONKEY INT,S_PHONE STRING,S_ACCTBAL DOUBLE,S_COMMENT STRING) ROW FORMAT DELIMITED FIELDS TERMINATED BY '|' STORED AS TEXTFILE""")
    lhive.sql(s"""LOAD DATA LOCAL INPATH '_datapath_/plusd/tpch/supplier.tbl' OVERWRITE INTO TABLE SUPPLIER""")
    lhive.sql(s"""CREATE TABLE PARTSUPP(PS_PARTKEY INT,PS_SUPPKEY INT,PS_AVAILQTY INT,PS_SUPPLYCOST DOUBLE,PS_COMMENT STRING) ROW FORMAT DELIMITED FIELDS TERMINATED BY '|' STORED AS TEXTFILE""")
    lhive.sql(s"""LOAD DATA LOCAL INPATH '_datapath_/plusd/tpch/partsupp.tbl' OVERWRITE INTO TABLE PARTSUPP""")
    lhive.sql(s"""CREATE TABLE NATION(N_NATIONKEY INT,N_NAME STRING,N_REGIONKEY INT,N_COMMENT STRING) ROW FORMAT DELIMITED FIELDS TERMINATED BY '|' STORED AS TEXTFILE""")
    lhive.sql(s"""LOAD DATA LOCAL INPATH '_datapath_/plusd/tpch/nation.tbl' OVERWRITE INTO TABLE NATION""")
    lhive.sql(s"""select ps_partkey, sum(ps_supplycost * ps_availqty) as value from partsupp, supplier, nation where ps_suppkey = s_suppkey and s_nationkey = n_nationkey and n_name = 'GERMANY' group by ps_partkey having sum(ps_supplycost * ps_availqty) > ( select sum(ps_supplycost * ps_availqty) * 0.0001000000 from partsupp, supplier, nation where ps_suppkey = s_suppkey and s_nationkey = n_nationkey and n_name = 'GERMANY' ) order by value desc""")
  }

}
