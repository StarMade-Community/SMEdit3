package smc.smedit.ent.cmd;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import smc.smedit.ent.data.Tag;
import smc.smedit.ent.logic.TagLogic;
import smc.smedit.ent.logic.TagUtils;

public class DumpEntityFile {

    private String[] mArgs;
    private File mTestFile;
    private String mIdent;

    public DumpEntityFile(String[] argv) {
        mArgs = argv;
        mIdent = "";
    }

    public void run() {
        parseArgs();
        try {
            FileInputStream fis = new FileInputStream(mTestFile);
            Tag obj = TagLogic.readFile(fis, true);
            Tag sub = TagUtils.lookup(obj, mIdent);
            if (sub == null) {
                System.err.println("Cannot find '" + mIdent + "'");
            } else {
                obj = sub;
            }
            TagUtils.dump(obj, "");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void parseArgs() {
        if (mArgs.length == 0) {
            System.err.println("Arg1 = filename");
            System.err.println("Arg2 = path to object (optional)");
            System.exit(0);
        }
        mTestFile = new File(mArgs[0]);
        if (mArgs.length > 1) {
            mIdent = mArgs[1];
        }
    }

    public static void main(String[] argv) {
        DumpEntityFile app = new DumpEntityFile(argv);
        app.run();
    }
}
