package io.github.agileek.maven;

import com.helger.jcodemodel.EClassType;
import com.helger.jcodemodel.JClassAlreadyExistsException;
import com.helger.jcodemodel.JCodeModel;
import com.helger.jcodemodel.JDefinedClass;
import com.helger.jcodemodel.JEnumConstant;
import com.helger.jcodemodel.JExpr;
import com.helger.jcodemodel.JFieldVar;
import com.helger.jcodemodel.JMethod;
import com.helger.jcodemodel.JMod;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.PrintStream;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.zip.CRC32;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

@Mojo(
        name = "generate",
        defaultPhase = LifecyclePhase.GENERATE_SOURCES
)
public class ChecksumFlywayMojo extends AbstractMojo {

    @Parameter(name = "locations", required = false)
    String[] locations;

    @Parameter(name = "location", defaultValue = "/db/migration")
    String location;

    @Parameter(name = "outputDirectory", defaultValue = "${project.build.directory}/generated-sources")
    String outputDirectory;

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    MavenProject project;

    @Parameter(name = "extensions", required = false)
    String[] extensions = {".java"};


    @Override
    public final void execute() throws MojoExecutionException, MojoFailureException {
        try {
            if (locations == null || locations.length == 0) {
                locations = new String[]{location};
            }
            JCodeModel javaFile = generateEnumWithFilesChecksum(getJavaFiles(project.getCompileSourceRoots(), locations));
            File file = new File(outputDirectory);
            //noinspection ResultOfMethodCallIgnored
            file.mkdirs();
            javaFile.build(file, (PrintStream) null);
            project.addCompileSourceRoot(outputDirectory);
        } catch (Exception e) {
            throw new MojoFailureException("Failure", e);
        }
    }

    JCodeModel generateEnumWithFilesChecksum(List<File> files) throws JClassAlreadyExistsException {
        JCodeModel codeModel = new JCodeModel();
        JDefinedClass enumClass = codeModel._class("io.github.agileek.flyway.JavaMigrationChecksums", EClassType.ENUM);
        JFieldVar checksumField = enumClass.field(JMod.PRIVATE | JMod.FINAL, int.class, "checksum");

        //Define the enum constructor
        JMethod enumConstructor = enumClass.constructor(JMod.PRIVATE);
        enumConstructor.param(int.class, "checksum");
        enumConstructor.body().assign(JExpr._this().ref("checksum"), JExpr.ref("checksum"));

        JMethod getterColumnMethod = enumClass.method(JMod.PUBLIC, int.class, "getChecksum");
        getterColumnMethod.body()._return(checksumField);

        for (File file : files) {
            JEnumConstant enumConst = enumClass.enumConstant(file.getName().split("\\.")[0]);
            enumConst.arg(JExpr.lit(computeFileChecksum(file)));
        }

        return codeModel;
    }

    List<File> getJavaFiles(List<String> compileSourceRoots, String... locations) {
        List<File> files = new ArrayList<File>();
        for (String compileSourceRoot : compileSourceRoots) {
            for (String location : locations) {
                File file = new File(compileSourceRoot + location);
                File[] java = file.listFiles(new FilenameFilter() {
                    @Override
                    public boolean accept(File dir, String name) {
                        for (String extension : extensions) {
                            if (name.endsWith(extension)) {
                                return true;
                            }
                        }
                        return false;
                    }
                });
                if (java != null) {
                    Collections.addAll(files, java);
                }
            }
        }
        return files;
    }

    int computeFileChecksum(File file) {
        final CRC32 crc32 = new CRC32();

        try {
            RandomAccessFile r = new RandomAccessFile(file, "r");
            byte[] b = new byte[(int) r.length()];
            r.readFully(b);
            crc32.update(b);
        } catch (IOException e) {
            String message = "Unable to calculate checksum for " + file.getAbsolutePath();
            throw new RuntimeException(message, e);
        }
        return (int) crc32.getValue();
    }
}