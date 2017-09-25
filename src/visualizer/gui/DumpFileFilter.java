package visualizer.gui;


import java.io.File;
import javax.swing.*;
import javax.swing.filechooser.*;

/* ImageFilter.java is used by FileChooserDemo2.java. */
public class DumpFileFilter extends FileFilter {

    //Accept all directories and all gif, jpg, tiff, or png files.
    public boolean accept(File f) {
        if (f.isDirectory()) {
            return true;
        }
        
        String extension = "";

        int i = f.getName().lastIndexOf('.');
        if (i > 0) {
            extension = f.getName().substring(i+1);
        }
        
        if (extension != null) {
            if (extension.equals("dump")){
                    return true;
            } else {
                return false;
            }
        }

        return false;
    }

    //The description of this filter
    public String getDescription() {
        return "Backprojection .dump file";
    }
}