package net.segner.maven.plugins.communal.module;


import net.java.truevfs.access.TFile;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.List;
import java.util.jar.Manifest;


public interface ApplicationModule {

    @Nonnull
    String getName();

    void setName(String name);

    boolean isUnpacked();

    void setUnpacked(TFile unpackedFile);

    TFile getModuleRoot();

    String getDefaultLibraryPath();

    void setLibraryPath(String libLocation);

    TFile getLibrary();

    void addLib(TFile library) throws IOException;

    void removeLib(String libraryName) throws IOException;

    List<TFile> getLibraryFiles();

    Manifest getManifest() throws IOException;

    void saveManifest(Manifest manifest) throws IOException;
}
