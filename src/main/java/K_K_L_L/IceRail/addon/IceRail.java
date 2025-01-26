package K_K_L_L.IceRail.addon;

import K_K_L_L.IceRail.addon.modules.*;
import com.mojang.logging.LogUtils;
import meteordevelopment.meteorclient.addons.GithubRepo;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import org.slf4j.Logger;

public class IceRail extends MeteorAddon {
    public static final Logger LOG = LogUtils.getLogger();
    public static final Category CATEGORY = new Category("IceRail");

    @Override
    public void onInitialize() {
        LOG.info("Initializing Ice Highway Builder.");

        // Modules
        Modules.get().add(new IceHighwayBuilder());
        Modules.get().add(new IceRailAutoReplenish());
        Modules.get().add(new IceRailNuker());
        Modules.get().add(new IceRailAutoEat());
        Modules.get().add(new ScaffoldGrim());
        Modules.get().add(new IcePlacer());
        Modules.get().add(new GatherItem());
    }

    @Override
    public void onRegisterCategories() {
        Modules.registerCategory(CATEGORY);
    }

    @Override
    public String getPackage() {
        return "K_K_L_L.IceRail.addon";
    }

    @Override
    public GithubRepo getRepo() {
        return new GithubRepo("K-K-L-L", "Ice-Highway-Builder");
    }
}
