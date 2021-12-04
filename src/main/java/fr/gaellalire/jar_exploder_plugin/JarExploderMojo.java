/*
 * Copyright 2021 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package fr.gaellalire.jar_exploder_plugin;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.commons.io.IOUtils;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;

/**
 * @author Gael Lalire
 */
@Mojo(name = "explode-jar", defaultPhase = LifecyclePhase.PACKAGE, requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME)
public class JarExploderMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Component
    private MavenProjectHelper mavenProjectHelper;

    public static String toHexString(final byte[] bytes) {
        if (bytes == null) {
            return null;
        }

        StringBuilder buffer = new StringBuilder(bytes.length * 2);

        for (byte aByte : bytes) {
            int b = aByte & 0xFF;
            if (b < 0x10) {
                buffer.append('0');
            }
            buffer.append(Integer.toHexString(b));
        }

        return buffer.toString();
    }

    static class AssemblyFile {
        int position;

        int subPosition;

        String name;

        public AssemblyFile(final int position, final int subPosition, final String name) {
            this.position = position;
            this.subPosition = subPosition;
            this.name = name;
        }

    }

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        try {
            File jarWithDependencies = project.getArtifact().getFile();

            String extension = project.getArtifact().getArtifactHandler().getExtension();
            boolean repackageExpected = "ear".equals(extension);

            Map<String, MetaAndSha512> metaBySha512 = new HashMap<String, MetaAndSha512>();
            SubMetaIndexer subMetaIndexer = new SubMetaIndexer();

            // calculate sha512 in dependencies
            getLog().info("Analyzing dependencies");
            for (org.apache.maven.artifact.Artifact artifact : project.getArtifacts()) {
                File dependency = artifact.getFile();
                String url;
                String classifier = artifact.getClassifier();
                String extension2 = artifact.getArtifactHandler().getExtension();
                if (classifier != null && classifier.length() != 0) {
                    url = "mvn:" + artifact.getGroupId() + "/" + artifact.getArtifactId() + "/" + artifact.getVersion() + "/" + extension2 + "/" + classifier;
                } else if ("jar".equals(extension2)) {
                    url = "mvn:" + artifact.getGroupId() + "/" + artifact.getArtifactId() + "/" + artifact.getVersion();
                } else {
                    url = "mvn:" + artifact.getGroupId() + "/" + artifact.getArtifactId() + "/" + artifact.getVersion() + "/" + extension2;
                }

                int position = 0;
                MessageDigest mainDigest = MessageDigest.getInstance("SHA-512");
                List<MetaAndSha512> subMetas = new ArrayList<MetaAndSha512>();
                try (ZipArchiveInputStream zis = new ZipArchiveInputStream(new DigestInputStream(new FileInputStream(dependency), mainDigest))) {
                    ZipArchiveEntry nextEntry = zis.getNextZipEntry();
                    while (nextEntry != null) {
                        position++;

                        if (repackageExpected) {
                            MessageDigest subDigest = MessageDigest.getInstance("SHA-512");
                            DigestInputStream digestInputStream = new DigestInputStream(zis, subDigest);
                            while (digestInputStream.read() != -1) {
                                ;
                            }

                            MetaAndSha512 metaAndSha512 = new MetaAndSha512(nextEntry.getName(), position, url, toHexString(subDigest.digest()), nextEntry.getExternalAttributes(),
                                    nextEntry.getTime());

                            subMetas.add(metaAndSha512);
                        } else {
                            while (zis.read() != -1) {
                                ;
                            }

                        }

                        nextEntry = zis.getNextZipEntry();
                    }

                }
                MetaAndSha512 metaAndSha512 = new MetaAndSha512(dependency.getName(), -1, url, toHexString(mainDigest.digest()), null, null);
                subMetaIndexer.add(metaAndSha512, subMetas);
                metaBySha512.put(metaAndSha512.getSha512(), metaAndSha512);
            }
            getLog().info("Dependencies analyzed");

            subMetaIndexer.setMetaBySha512(metaBySha512);

            // compare with sha512 in assembly and create properties and fileMetas
            Properties properties = new Properties();
            // explodedJarFile should be attach to the original artifact with
            // exploded-assembly classifier and keep same extension than original
            // artifact

            String fileName = project.getArtifactId() + "-" + project.getVersion() + "-exploded-assembly." + extension;
            getLog().info("Creating " + fileName);
            File explodedJarFile = new File(project.getBuild().getDirectory(), fileName);
            try (ZipArchiveOutputStream zos = new ZipArchiveOutputStream(new FileOutputStream(explodedJarFile))) {
                int position = 0;
                int depNum = 0;
                int explodedFile = 0;
                List<String> urlList = new ArrayList<String>();
                List<AssemblyFile> assemblyFiles = new ArrayList<AssemblyFile>();
                // use ZipArchiveInputStream to get the correct order
                // use ZipFile to fetch attributes in central header
                try (ZipArchiveInputStream zis = new ZipArchiveInputStream(new FileInputStream(jarWithDependencies)); ZipFile zipFile = new ZipFile(jarWithDependencies)) {
                    ZipArchiveEntry nextEntry = zis.getNextZipEntry();
                    while (nextEntry != null) {
                        position++;
                        String name = nextEntry.getName();
                        nextEntry = zipFile.getEntry(name);
                        if (nextEntry.isDirectory() || nextEntry.getSize() == 0) {
                            zos.putArchiveEntry(nextEntry);
                            zos.closeArchiveEntry();
                            nextEntry = zis.getNextZipEntry();
                            continue;
                        }
                        MessageDigest mainDigest = MessageDigest.getInstance("SHA-512");
                        DigestInputStream dis = new DigestInputStream(zis, mainDigest);
                        boolean validZip = false;
                        List<MetaAndSha512> subMetas = new ArrayList<MetaAndSha512>();
                        @SuppressWarnings("resource") // cannot close dis, it will
                                                      // also close zis
                        ZipArchiveInputStream subZis = new ZipArchiveInputStream(dis);
                        try {
                            int subPosition = 0;
                            ZipArchiveEntry subNextEntry = subZis.getNextZipEntry();
                            while (subNextEntry != null) {
                                validZip = true;
                                subPosition++;

                                if (repackageExpected) {
                                    // this will be used for ear where war may
                                    // be repackage (skinny option), we may need
                                    // to reorder entries change time and
                                    // replace some modified file (like
                                    // manifest)
                                    MessageDigest subDigest = MessageDigest.getInstance("SHA-512");
                                    DigestInputStream digestInputStream = new DigestInputStream(subZis, subDigest);
                                    while (digestInputStream.read() != -1) {
                                        ;
                                    }
                                    subMetas.add(new MetaAndSha512(subNextEntry.getName(), subPosition, null, toHexString(subDigest.digest()), subNextEntry.getExternalAttributes(),
                                            subNextEntry.getTime()));
                                } else {
                                    while (subZis.read() != -1) {
                                        ;
                                    }
                                }

                                subNextEntry = subZis.getNextZipEntry();
                            }
                        } catch (Exception e) {
                            validZip = false;
                        }

                        while (dis.read() != -1) {
                            ;
                        }

                        String sha512 = toHexString(mainDigest.digest());
                        boolean found = false;
                        if (validZip) {
                            MetaAndSha512 dependencyMetaAndSha512 = metaBySha512.get(sha512);

                            if (dependencyMetaAndSha512 != null) {
                                found = true;
                                depNum++;
                                String property = properties.getProperty("dependencies");
                                if (property == null) {
                                    properties.setProperty("dependencies", "dep" + depNum);
                                } else {
                                    properties.setProperty("dependencies", property + ",dep" + depNum);
                                }
                                // following data are mandatory, help to reproduce
                                // the archive content as it was
                                properties.setProperty("dep" + depNum + ".position", String.valueOf(position));
                                properties.setProperty("dep" + depNum + ".name", nextEntry.getName());
                                properties.setProperty("dep" + depNum + ".externalAttributes", String.valueOf(nextEntry.getExternalAttributes()));
                                properties.setProperty("dep" + depNum + ".time", String.valueOf(nextEntry.getTime()));
                                properties.setProperty("dep" + depNum + ".url", dependencyMetaAndSha512.getUrl());

                            } else {
                                RepackagedJar repackagedJar = subMetaIndexer.search(subMetas);
                                if (repackagedJar != null) {
                                    List<MetaAndSha512> filesToAdd = repackagedJar.getFilesToAdd();
                                    StringBuilder fileNames = new StringBuilder();
                                    boolean subFirst = true;
                                    for (MetaAndSha512 fileMeta : filesToAdd) {
                                        explodedFile++;
                                        if (subFirst) {
                                            subFirst = false;
                                        } else {
                                            fileNames.append(",");
                                        }
                                        fileNames.append("file");
                                        fileNames.append(explodedFile);
                                        properties.setProperty("file" + explodedFile + ".name", fileMeta.getName());
                                        properties.setProperty("file" + explodedFile + ".externalAttributes", String.valueOf(fileMeta.getExternalAttributes()));
                                        properties.setProperty("file" + explodedFile + ".time", String.valueOf(fileMeta.getTime()));
                                        properties.setProperty("file" + explodedFile + ".position", String.valueOf(fileMeta.getPosition()));
                                        assemblyFiles.add(new AssemblyFile(position, fileMeta.getPosition(), "META-INF/exploded-assembly-files/file" + explodedFile));
                                    }

                                    found = true;
                                    depNum++;
                                    String property = properties.getProperty("dependencies");
                                    if (property == null) {
                                        properties.setProperty("dependencies", "dep" + depNum);
                                    } else {
                                        properties.setProperty("dependencies", property + ",dep" + depNum);
                                    }
                                    // following data are mandatory, help to
                                    // reproduce
                                    // the archive content as it was
                                    properties.setProperty("dep" + depNum + ".position", String.valueOf(position));
                                    properties.setProperty("dep" + depNum + ".name", nextEntry.getName());
                                    properties.setProperty("dep" + depNum + ".externalAttributes", String.valueOf(nextEntry.getExternalAttributes()));
                                    properties.setProperty("dep" + depNum + ".time", String.valueOf(nextEntry.getTime()));
                                    properties.setProperty("dep" + depNum + ".url", repackagedJar.getUrl());
                                    properties.setProperty("dep" + depNum + ".repackage.positions", repackagedJar.getPositions());
                                    properties.setProperty("dep" + depNum + ".repackage.times", repackagedJar.getTimes());
                                    properties.setProperty("dep" + depNum + ".repackage.externalAttributesList", repackagedJar.getExternalAttributesList());
                                    properties.setProperty("dep" + depNum + ".repackage.files", fileNames.toString());
                                    StringBuilder excludedURLsSB = new StringBuilder();
                                    boolean firstExcludedURL = true;
                                    List<String> excludedURLs = repackagedJar.getExcludedURLs();
                                    if (excludedURLs != null && excludedURLs.size() != 0) {
                                        for (String excludedURL : excludedURLs) {
                                            int indexOf = urlList.indexOf(excludedURL) + 1;
                                            if (indexOf == 0) {
                                                urlList.add(excludedURL);
                                                indexOf = urlList.size();
                                                properties.setProperty("url" + indexOf, excludedURL);
                                            }
                                            if (firstExcludedURL) {
                                                firstExcludedURL = false;
                                            } else {
                                                excludedURLsSB.append(",");
                                            }
                                            excludedURLsSB.append("url");
                                            excludedURLsSB.append(indexOf);
                                        }
                                        properties.setProperty("dep" + depNum + ".repackage.excludedURLs", excludedURLsSB.toString());
                                    }

                                }
                            }
                        }
                        if (!found) {
                            // not a dependency, copy as it is (no space gain)
                            zos.addRawArchiveEntry(nextEntry,
                                    new SkipAndLimitFilterInputStream(new FileInputStream(jarWithDependencies), nextEntry.getDataOffset(), nextEntry.getCompressedSize()));
                            zos.flush();
                        }

                        nextEntry = zis.getNextZipEntry();
                    }
                }

                // add our property file (space lost !!!)
                File explodedAssemblyFile = new File(project.getBuild().getDirectory(), "exploded-assembly.properties");
                properties.store(new FileOutputStream(explodedAssemblyFile), null);
                zos.putArchiveEntry(new ZipArchiveEntry(explodedAssemblyFile, "META-INF/exploded-assembly.properties"));
                IOUtils.copy(new FileInputStream(explodedAssemblyFile), zos);
                zos.closeArchiveEntry();
                explodedAssemblyFile.delete();

                for (AssemblyFile assemblyFile : assemblyFiles) {
                    zos.putArchiveEntry(new ZipArchiveEntry(assemblyFile.name));
                    position = 0;
                    try (ZipArchiveInputStream zis = new ZipArchiveInputStream(new FileInputStream(jarWithDependencies))) {
                        ZipArchiveEntry nextEntry = zis.getNextZipEntry();
                        while (nextEntry != null) {
                            position++;
                            if (position == assemblyFile.position) {
                                int subPosition = 0;
                                ZipArchiveInputStream subZis = new ZipArchiveInputStream(zis);
                                ZipArchiveEntry nextSubEntry = subZis.getNextZipEntry();
                                while (nextSubEntry != null) {
                                    subPosition++;
                                    if (subPosition == assemblyFile.subPosition) {
                                        IOUtils.copy(subZis, zos);
                                        break;
                                    }
                                    nextSubEntry = subZis.getNextZipEntry();
                                }
                                break;
                            }
                            nextEntry = zis.getNextZipEntry();
                        }
                    }

                    zos.closeArchiveEntry();
                }
            }
            getLog().info(fileName + " created");

            mavenProjectHelper.attachArtifact(project, explodedJarFile, "exploded-assembly");

        } catch (Exception e) {
            throw new MojoExecutionException("Artifact could not be resolved.", e);
        }
    }

}
