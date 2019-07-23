/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package com.archimatetool.editor.p2;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.net.URL;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.BusyIndicator;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Shell;
import org.osgi.framework.Bundle;

import com.archimatetool.editor.ArchiPlugin;
import com.archimatetool.editor.Logger;
import com.archimatetool.editor.utils.FileUtils;
import com.archimatetool.editor.utils.PlatformUtils;
import com.archimatetool.editor.utils.ZipUtils;


/**
 * @author Phillip Beauvoir
 */
public class DropinsPluginHandler {
    
    private Shell shell;
    
    private File dropinsFolder;

    private boolean success;
    private boolean needsClose;
    
    static final int CONTINUE = 0;
    static final int RESTART = 1;
    static final int CLOSE = 2;
    
    static final String MAGIC_ENTRY = "archi-plugin"; //$NON-NLS-1$
    
    public DropinsPluginHandler() { 
    }

    public List<Bundle> getInstalledPlugins() throws IOException {
        List<Bundle> list = new ArrayList<Bundle>();
        
        for(Bundle bundle : ArchiPlugin.INSTANCE.getBundle().getBundleContext().getBundles()) {
            File file = getBundleLocation(bundle);
            if(file != null) {
                list.add(bundle);
            }
        }
        
        return list;
    }
    
    public int install(Shell shell) throws IOException {
        this.shell = shell;
        
        if(!checkCanWrite()) {
            return status();
        }
        
        List<File> files = askOpenFiles();
        if(files.isEmpty()) {
            return status();
        }
        
        List<IStatus> stats = new ArrayList<IStatus>();
        
        Exception[] exception = new Exception[1];
        
        BusyIndicator.showWhile(Display.getCurrent(), new Runnable() {
            @Override
            public void run() {
                for(File file : files) {
                    try {
                        IStatus status = installFile(file);
                        stats.add(status);
                    }
                    catch(IOException ex) {
                        exception[0] = ex;
                    }
                }
            }
        });
        
        if(exception[0] != null) {
            displayErrorDialog(exception[0].getMessage());
            return status();
        }
            
        String resultMessage = ""; //$NON-NLS-1$
        boolean hasError = false;
        
        for(int i = 0; i < stats.size(); i++) {
            IStatus status = stats.get(i);
            
            if(status.isOK()) {
                success = true;
                resultMessage += NLS.bind(Messages.DropinsPluginHandler_2 + "\n", files.get(i).getName()); //$NON-NLS-1$
            }
            else {
                hasError = true;
                
                if(status.getCode() == 666) {
                    resultMessage += NLS.bind(Messages.DropinsPluginHandler_3 + "\n", files.get(i).getName()); //$NON-NLS-1$
                }
                else {
                    resultMessage += NLS.bind(Messages.DropinsPluginHandler_4 + "\n", files.get(i).getName()); //$NON-NLS-1$
                }
            }
        }
        
        if(hasError) {
            MessageDialog.openInformation(shell, Messages.DropinsPluginHandler_5, resultMessage);
        }
        
        return status();
    }
    
    private IStatus installFile(File zipFile) throws IOException {
        if(!isPluginZipFile(zipFile)) {
            return new Status(IStatus.ERROR, "com.archimatetool.editor", 666, //$NON-NLS-1$
                    NLS.bind(Messages.DropinsPluginHandler_6, zipFile.getAbsolutePath()), null);
        }
            
        Path tmp = Files.createTempDirectory("archi"); //$NON-NLS-1$
        File tmpFolder = tmp.toFile();
        
        try {
            ZipUtils.unpackZip(zipFile, tmpFolder);
            
            File pluginsFolder = getDropinsFolder(true);

            for(File file : tmpFolder.listFiles()) {
                // Ignore the magic entry file
                if(MAGIC_ENTRY.equalsIgnoreCase(file.getName())) {
                    continue;
                }
                
                // Delete old plugin on exit in target plugins folder
                deleteOlderPluginOnExit(file, pluginsFolder);

                // Copy new ones
                if(file.isDirectory()) {
                    FileUtils.copyFolder(file, new File(pluginsFolder, file.getName()));
                }
                else {
                    FileUtils.copyFile(file, new File(pluginsFolder, file.getName()), false);
                }
            }
        }
        finally {
            FileUtils.deleteFolder(tmpFolder);
        }

        return new Status(IStatus.OK, "com.archimatetool.editor", 777, NLS.bind(Messages.DropinsPluginHandler_0, zipFile.getPath()), null); //$NON-NLS-1$
    }

    public int uninstall(Shell shell, List<Bundle> selected) throws IOException {
        if(selected.isEmpty()) {
            return status();
        }
        
        if(!checkCanWrite()) {
            return status();
        }
        
        boolean ok = MessageDialog.openQuestion(shell,
                Messages.DropinsPluginHandler_7,
                Messages.DropinsPluginHandler_8);
        
        if(!ok) {
            return status();
        }
        
        for(Bundle bundle : selected) {
            File file = getBundleLocation(bundle);
            if(file != null) {
                deleteOnExit(file);
            }
            else {
                Logger.logError(NLS.bind(Messages.DropinsPluginHandler_1, bundle.getLocation()));
            }
        }
        
        success = true;
        
        return status();
    }
    
    private int status() {
        if(success && needsClose) {
            return CLOSE;
        }
        if(success) {
            return RESTART;
        }
        
        return CONTINUE;
    }
    
    // Delete matching older plugin
    private void deleteOlderPluginOnExit(File newPlugin, File pluginsFolder) throws IOException {
        for(File file : findMatchingPlugins(pluginsFolder, newPlugin)) {
            deleteOnExit(file);
        }
    }
    
    private File[] findMatchingPlugins(File pluginsFolder, File newPlugin) {
        String pluginName = getPluginName(newPlugin.getName());
        
        return pluginsFolder.listFiles(new FileFilter() {
            @Override
            public boolean accept(File file) {
                String targetPluginName = getPluginName(file.getName());
                return targetPluginName.equals(pluginName) && !newPlugin.getName().equals(file.getName());
            }
        });
    }
    
    String getPluginName(String string) {
        int index = string.indexOf("_"); //$NON-NLS-1$
        if(index != -1) {
            string = string.substring(0, index);
        }
        
        return string;
    }

    String getPluginVersion(String string) {
        int index = string.lastIndexOf(".jar"); //$NON-NLS-1$
        if(index != -1) {
            string = string.substring(0, index);
        }
        
        index = string.lastIndexOf("_"); //$NON-NLS-1$
        if(index != -1) {
            string = string.substring(index + 1);
        }
        
        return string;
    }

    private boolean checkCanWrite() throws IOException {
        if(!canWriteToDropinsFolder()) {
            String message = Messages.DropinsPluginHandler_9 + " "; //$NON-NLS-1$
            
            if(PlatformUtils.isWindows()) {
                message += Messages.DropinsPluginHandler_10;
            }
            else {
                message += Messages.DropinsPluginHandler_11;
            }
            
            displayErrorDialog(message);
            
            return false;
        }
        
        return true;
    }

    private boolean canWriteToDropinsFolder() throws IOException {
        File folder = getDropinsFolder(true);
        return Files.isWritable(folder.toPath());
    }
    
    boolean isPluginZipFile(File file) throws IOException {
        return ZipUtils.isZipFile(file) && ZipUtils.hasZipEntry(file, MAGIC_ENTRY);
    }
    
    private File getDropinsFolder(boolean doCreate) throws IOException {
        if(dropinsFolder == null) {
            URL url = Platform.getInstallLocation().getURL();
            url = FileLocator.resolve(url);
            dropinsFolder = new File(url.getPath(), "dropins"); //$NON-NLS-1$
            if(doCreate) {
                dropinsFolder.mkdirs();
            }
        }
        
        return dropinsFolder;
    }
    
    private File getBundleLocation(Bundle bundle) throws IOException {
        String location = bundle.getLocation().replace("reference:", "") //$NON-NLS-1$ //$NON-NLS-2$
                .replace("file:", "") //$NON-NLS-1$ //$NON-NLS-2$
                .replace("file:/", ""); //$NON-NLS-1$ //$NON-NLS-2$

        File bundleFile = new File(location);
        File fullFile = new File(getDropinsFolder(false), bundleFile.getName());

        return fullFile.exists() ? fullFile : null;
    }

    private List<File> askOpenFiles() {
        FileDialog dialog = new FileDialog(shell, SWT.OPEN | SWT.MULTI);
        dialog.setFilterExtensions(new String[] { "*.zip", "*.*" } ); //$NON-NLS-1$ //$NON-NLS-2$
        String path = dialog.open();
        
        // TODO: Bug on Mac 10.12 and newer - Open dialog does not close straight away
        // See https://bugs.eclipse.org/bugs/show_bug.cgi?id=527306
        if(path != null && PlatformUtils.isMac()) {
            while(Display.getCurrent().readAndDispatch());
        }
        
        List<File> files = new ArrayList<File>();
        
        if(path != null) {
            for(String name : dialog.getFileNames()) {
                String filterPath = dialog.getFilterPath();
                filterPath += File.separator; // Issue on OpenJDK if path is like C: or D: - no slash is added when creating File
                files.add(new File(filterPath, name));
            }
        }
        
        return files;
    }

    private void deleteOnExit(File file) throws IOException {
        if(file.isDirectory()) {
            recursiveDeleteOnExit(file.toPath());
        }
        else {
            file.deleteOnExit();
        }
        
        // Mac won't delete files with File.deleteOnExit() if workbench is restarted
        needsClose = PlatformUtils.isMac();
    }
    
    private void recursiveDeleteOnExit(Path path) throws IOException {
        Files.walkFileTree(path, new SimpleFileVisitor<Path>() {

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                file.toFile().deleteOnExit();
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                dir.toFile().deleteOnExit();
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private void displayErrorDialog(String message) {
        MessageDialog.openError(shell,
                Messages.DropinsPluginHandler_12,
                message);
    }
}
