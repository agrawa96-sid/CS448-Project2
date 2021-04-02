package simpledb.query;

import simpledb.file.BlockId;
import simpledb.record.Layout;
import simpledb.record.RecordPage;
import simpledb.record.Schema;
import simpledb.tx.Transaction;
import static java.sql.Types.INTEGER;

public class BNLJScan implements Scan {
    private static int count = 0;

    class PageScan implements Scan {
        private Transaction tx;
        private Layout layout;
        private Scan scan;
        private RecordPage rp;
        private int currentslot;
        private String fname;
        private boolean isdone = false;

        public PageScan(Transaction tx, Layout layout, Scan scan, String fname) {
            this.tx = tx;
            this.layout = layout;
            this.scan = scan;
            this.currentslot = -1;
            this.fname = fname;
            scan.beforeFirst();
            loadNext();
        }

        public int loadNext() {
            if (isdone)
                return 0;
            int loadcount = 0;
            if (rp == null || rp.block().number() >= tx.size(fname) - 1) {
                moveToNewBlock();
                while ((currentslot = rp.insertAfter(currentslot)) >= 0) {
                    if (!scan.next()) {
                        isdone = true;
                        break;
                    }
                    for (String fldname : layout.schema().fields()) {
                        if (layout.schema().type(fldname) == INTEGER)
                            rp.setInt(currentslot, fldname, scan.getInt(fldname));
                        else
                            rp.setString(currentslot, fldname, scan.getString(fldname));
                    }
                    loadcount++;
                }
            } else {
                moveToBlock(rp.block().number() + 1);
                loadcount = 1;
            }
            currentslot = -1;
            return loadcount;
        }

        @Override
        public boolean next() {
            currentslot = rp.nextAfter(currentslot);
            return currentslot >= 0;
        }

        @Override
        public void beforeFirst() {
            currentslot = -1;
        }

        @Override
        public int getInt(String fldname) {
            return rp.getInt(currentslot, fldname);
        }

        @Override
        public String getString(String fldname) {
            return rp.getString(currentslot, fldname);
        }

        @Override
        public Constant getVal(String fldname) {
            if (layout.schema().type(fldname) == INTEGER)
                return new Constant(rp.getInt(currentslot, fldname));
            return new Constant(rp.getString(currentslot, fldname));
        }

        @Override
        public boolean hasField(String fldname) {
            return scan.hasField(fldname);
        }

        public void reset() {
            moveToBlock(0);
        }

        public void close() {
            if (rp != null)
                tx.unpin(rp.block());
        }

        private void moveToBlock(int blknum) {
            close();
            BlockId blk = new BlockId(fname, blknum);
            rp = new RecordPage(tx, blk, layout);
            currentslot = -1;
        }

        private void moveToNewBlock() {
            close();
            BlockId blk = tx.append(fname);
            rp = new RecordPage(tx, blk, layout);
            rp.format();
            currentslot = -1;
        }
    }

    private Predicate pred;
    private Layout layout;

    private PageScan r, s;

    public BNLJScan(Transaction tx, Layout layout, Scan r, Scan s, Predicate pred) {
        this.layout = layout;
        this.r = new PageScan(tx, projectLayout(r), r, "bnlj-" + (count++) + ".tbl");
        this.s = new PageScan(tx, projectLayout(s), s, "bnlj-" + (count++) + ".tbl");
        this.pred = pred;
        beforeFirst();
    }

    public Layout projectLayout(Scan s) {
        Schema schema = new Schema();
        for (String fldname : layout.schema().fields()) {
            if (s.hasField(fldname))
                schema.addField(fldname, layout.schema().type(fldname), layout.schema().length(fldname));
        }
        return new Layout(schema);
    }

    @Override
    public void beforeFirst() {
        r.beforeFirst();
        r.next();
        s.beforeFirst();
    }

    @Override
    public boolean next() {
        while (true) {
            // innermost loop
            while (s.next()) {
               // Check if both Values are existing or not
                if (pred.isSatisfied(this) && !(s.getVal("d").equals(new Constant(""))) && !r.getVal("b").equals(new Constant(""))) {
                //if (pred.isSatisfied(this))  {
                    return true;
                }
            }
            // Bs exhausted, load next in Br
            if (!r.next()) {
                // Br exhausted, load next in s
                if (s.loadNext() == 0) {
                    // s exhausted, load next in r
                    if (r.loadNext() == 0) {
                        // r exhausted, done
                        return false;
                    }
                    // next r is loaded, reset s
                    s.reset();
                    s.loadNext();
                }
                // next s is loaded, reset Br
                r.beforeFirst();
                r.next();
            }
            // next Br is loaded, reset Bs
            s.beforeFirst();
        }
    }

    @Override
    public int getInt(String fldname) {
        if (r.hasField(fldname))
            return r.getInt(fldname);
        else
            return s.getInt(fldname);
    }

    @Override
    public String getString(String fldname) {
        if (r.hasField(fldname))
            return r.getString(fldname);
        else
            return s.getString(fldname);
    }

    @Override
    public Constant getVal(String fldname) {
        if (r.hasField(fldname))
            return r.getVal(fldname);
        else
            return s.getVal(fldname);
    }

    @Override
    public boolean hasField(String fldname) {
        return r.hasField(fldname) || s.hasField(fldname);
    }

    @Override
    public void close() {
        r.close();
        s.close();
    }
}
