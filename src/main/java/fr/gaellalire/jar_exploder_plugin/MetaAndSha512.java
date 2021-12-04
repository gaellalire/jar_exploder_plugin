package fr.gaellalire.jar_exploder_plugin;

public class MetaAndSha512 {
    
    private String name;

    private int position;

    private String url;

    private String sha512;
    
    private Long externalAttributes;
    
    private Long time;

    public MetaAndSha512(String name, int position, String url, String sha512, Long externalAttributes, Long time) {
        this.name = name;
        this.position = position;
        this.url = url;
        this.sha512 = sha512;
        this.externalAttributes = externalAttributes;
        this.time = time;
    }

    public String getName() {
        return name;
    }

    public int getPosition() {
        return position;
    }

    public String getUrl() {
        return url;
    }

    public String getSha512() {
        return sha512;
    }

    public Long getExternalAttributes() {
        return externalAttributes;
    }

    public Long getTime() {
        return time;
    }

    @Override
    public String toString() {
        return "MetaAndSha512 [name=" + name + ", position=" + position + ", url=" + url + ", sha512=" + sha512 + ", externalAttributes=" + externalAttributes + ", time=" + time
                + "]";
    }
    

}
