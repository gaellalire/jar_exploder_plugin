package fr.gaellalire.jar_exploder_plugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SubMetaIndexer {
    
    public static final String EMPTY_SHA512 = "cf83e1357eefb8bdf1542850d66d8007d620e4050b5715dc83f4a921d36ce9ce47d0d13c5d85f2b0ff8318d2877eec2f63b931bd47417a81a538327af927da3e";

    private class MetaAndSubMeta {
        MetaAndSha512 meta;

        List<MetaAndSha512> subMetas;

        public MetaAndSubMeta(MetaAndSha512 meta, List<MetaAndSha512> subMetas) {
            this.meta = meta;
            this.subMetas = subMetas;
        }

        public RepackagedJar repackage(List<MetaAndSha512> assembledSubMetas) {
            List<String> excludedURLs = new ArrayList<String>();
            StringBuilder positions = new StringBuilder();
            StringBuilder times = new StringBuilder();
            StringBuilder externalAttributesSB = new StringBuilder();
            List<MetaAndSha512> subMetasUnused = new ArrayList<MetaAndSha512>(this.subMetas);
            List<MetaAndSha512> filesToAdd = new ArrayList<MetaAndSha512>();
            boolean first = true;
            mainl: for (MetaAndSha512 assembledSubMeta : assembledSubMetas) {
                String name = assembledSubMeta.getName();
                long time = assembledSubMeta.getTime();
                long externalAttributes = assembledSubMeta.getExternalAttributes();
                int position = 0;
                for (MetaAndSha512 subMeta : this.subMetas) {
                    position++;
                    if (!name.equals(subMeta.getName())) {
                        continue;
                    }
                    if (!assembledSubMeta.getSha512().equals(subMeta.getSha512())) {
                        if ("META-INF/MANIFEST.MF".equalsIgnoreCase(name)) {
                            // see later
                            filesToAdd.add(assembledSubMeta);
                            continue mainl;
                        }
                        continue;
                    }
                    String timeS = "_";
                    String externalAttributesS = "_";
                    if (time != subMeta.getTime()) {
                        timeS = String.valueOf(time);
                    }
                    if (externalAttributes != subMeta.getExternalAttributes()) {
                        externalAttributesS = String.valueOf(externalAttributes);
                    }
                    if (first) {
                        first = false;
                    } else {
                        positions.append(",");
                        times.append(",");
                        externalAttributesSB.append(",");
                    }
                    positions.append(position);
                    times.append(timeS);
                    externalAttributesSB.append(externalAttributesS);
                    subMetasUnused.remove(subMeta);
                    continue mainl;
                }
                // not found
                return null;
            }
            for (MetaAndSha512 subMeta : subMetasUnused) {
                MetaAndSha512 metaAndSha512 = metaBySha512.get(subMeta.getSha512());
                if (metaAndSha512 == null) {
                    if ("META-INF/MANIFEST.MF".equalsIgnoreCase(subMeta.getName())) {
                        // ok to be skipped
                        continue;
                    }
                    return null;
                }
                String url = metaAndSha512.getUrl();
                excludedURLs.add(url);
            }
            

            return new RepackagedJar(positions.toString(), times.toString(), externalAttributesSB.toString(), excludedURLs, meta.getUrl(), filesToAdd);
        }

    }

    
    private Map<String, MetaAndSha512> metaBySha512;
    
    private Map<String, Set<MetaAndSubMeta>> subMetasMap = new HashMap<String, Set<MetaAndSubMeta>>();
    
    public void setMetaBySha512(Map<String, MetaAndSha512> metaBySha512) {
        this.metaBySha512 = metaBySha512;
    }

    public void add(MetaAndSha512 original, List<MetaAndSha512> subMetas) {
        MetaAndSubMeta metaAndSubMeta = new MetaAndSubMeta(original, subMetas);
        for (MetaAndSha512 subMeta : subMetas) {
            Set<MetaAndSubMeta> list = subMetasMap.get(subMeta.getSha512());
            if (list == null) {
                list = new HashSet<MetaAndSubMeta>();
                String sha512 = subMeta.getSha512();
                if (!EMPTY_SHA512.equals(sha512)) {
                    subMetasMap.put(sha512, list);
                }
            }
            if (!list.contains(metaAndSubMeta)) {
                list.add(metaAndSubMeta);
            }
        }
    }

    public RepackagedJar search(List<MetaAndSha512> assembledSubMetas) {
        Set<MetaAndSubMeta> set = new HashSet<MetaAndSubMeta>();
        for (MetaAndSha512 subMeta : assembledSubMetas) {
            Set<MetaAndSubMeta> c = subMetasMap.get(subMeta.getSha512());
            if (c != null)
                set.addAll(c);
        }
        for (MetaAndSubMeta metaAndSubMeta : set) {
            RepackagedJar repackagedJar = metaAndSubMeta.repackage(assembledSubMetas);
            if (repackagedJar != null) {
                return repackagedJar;
            }
        }
        return null;
    }

}
