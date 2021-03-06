package net.segner.maven.plugins.communal.module;

import net.java.truevfs.access.TFile;

public class RarModule extends GenericApplicationModule implements ApplicationModule {

    public static final String DEFAULT_WEBMODULE_LIBPATH = "";
    public static final String EXTENSION = "rar";

    RarModule() {
    }

    public RarModule(TFile archivePath) {
        super(archivePath);
    }

    @Override
    public String getDefaultLibraryPath() {
        return DEFAULT_WEBMODULE_LIBPATH;
    }

    void init(TFile path) {
        super.init(path);
    }

}
