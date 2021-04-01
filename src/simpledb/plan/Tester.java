package simpledb.plan;

import simpledb.server.SimpleDB;
import simpledb.tx.Transaction;
import simpledb.query.Scan;

import java.io.File;

public class Tester {
    private static final String fname = "plannertest3";
    private static Planner planner;
    private static Transaction tx;

    private static final int N = 20;

    private static long timer;

    private static int tableCount;
    private static int colCount;

    public static void deleteDir(File f) {
        if (f.isDirectory()) {
            for (File ff : f.listFiles()) {
                deleteDir(ff);
            }
        }
        f.delete();
    }

    public static void insert(int start, int end, int step) {
        String tbl = "T" + (++tableCount);
        char col1 = (char)('A' + (colCount++));
        char col2 = (char)('A' + (colCount++));

        String cmd = String.format("create table %s(%c int, %c varchar(9))", tbl, col1, col2);
        planner.executeUpdate(cmd, tx);
        if (step > 0) {
            for (int i = start; i < end; i += step) {
                int a = i;
                String b = "" + col2 + a;
                //cmd = String.format("insert into %s(%c, %c) values(%s, '%s')", tbl, col1, col2, a, b);
                //System.out.println(cmd);
                planner.executeUpdate(cmd, tx);
            }
        } else if (step < 0) {
            for (int i = start; i >= end; i -= step) {
                int a = i;
                String b = "" + col2 + a;
                //cmd = String.format("insert into %s(%c, %c) values(%s, %s)", tbl, col1, col2, a, b);
                //System.out.println(cmd);
                planner.executeUpdate(cmd, tx);
            }
        }
    }

    public static long runTest(QueryPlannerTest.JoinPlan plan) {
        tableCount = 0;
        colCount = 0;

        File ff = new File(fname);
        deleteDir(ff);

        SimpleDB db = new SimpleDB("plannertest3");
        tx = db.newTx();
        planner = db.planner();

        insert(0, 20, 1);
        insert(0, 10, 1);
        insert(0, N, 1);
        String qry = "select B,D from T1,T2 where A=C";
        Plan p = planner.createQueryPlan(qry, tx, plan);
        Scan s = p.open();
        while (s.next())
            System.out.println(s.getString("b") + " " + s.getString("d"));
        s.close();
        tx.commit();
        return System.currentTimeMillis();
    }

    public static void main(String[] args) {
        timer = System.currentTimeMillis();
        long total = runTest(QueryPlannerTest.JoinPlan.BLOCK_NESTED_LOOP_JOIN) - timer;
        double t = total/Math.pow(10,3);
        System.out.println("Total Runtime = " + t + " secs");
    }
}
