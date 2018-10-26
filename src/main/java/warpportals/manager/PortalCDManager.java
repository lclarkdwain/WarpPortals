package warpportals.manager;

import cn.nukkit.Player;
import cn.nukkit.block.Block;
import cn.nukkit.block.BlockID;
import warpportals.objects.PortalCreate;

import java.util.logging.Logger;

public class PortalCDManager {

    Logger mLogger;
    PortalDataManager iPDM;
    PortalToolManager iPTM;
    Config iConfig;

    public PortalCDManager(PortalDataManager pim, PortalToolManager ptm, Config portalConfig) {
        iPDM = pim;
        iPTM = ptm;
        iConfig = portalConfig;
    }

    void possibleDeletePortal(BlockID block, Player player) {
        if (block.getType() == Material.PORTAL || block.getType() == Material.ENDER_PORTAL) {
            deletePortal(block.getLocation());
            mPTM.removeTool(player.getUniqueId());
        } else
            player.sendMessage("Right click on the Portal that you want to delete");
    }

    public void deletePortal(String name) {
        try {
            Location loc = new Location(mPDM.getPortalInfo(name).tpCoords.world, 0, 0, 0);
            changeMaterial(Material.GOLD_BLOCK, mPDM.getPortalInfo(name).blockCoordArray, loc);
        } catch (Exception e) {
            // Error changing portal to gold block
        }
        mPDM.removePortal(name);
    }

    private void deletePortal(Location loc) {
        String delPortalName = "~|~";
        for (String portalName : mPDM.getPortalNames()) {
            for (Coords crd : mPDM.getPortalInfo(portalName).blockCoordArray) {
                if (loc.getX() == crd.x && loc.getY() == crd.y && loc.getZ() == crd.z) {
                    delPortalName = portalName;
                    break;
                }
            }
        }
        if (!delPortalName.matches("~|~"))
            deletePortal(delPortalName);
    }

    void possibleCreatePortal(Block block, Player player, PortalCreate portalCreate) {
        if (block.getType() == Material.GOLD_BLOCK || (block.getType() == Material.PORTAL || block.getType() == Material.ENDER_PORTAL)) {
            // Check to see if that Portal Name is already in use
            if (mPDM.getPortalInfo(portalCreate.portalName) == null) {
                // Check if Portal Name is valid
                if (portalCreate.portalName.matches(Regex.PORTAL_DEST_NAME)) {
                    /*
                     * Run recursion spider starting at the block the player
                     * clicked
                     */
                    int maxPortalSize = mPortalConfig.getInt("portals.create.maxSize", BlockCrawler.DEFAULT_MAX_SIZE);
                    BlockCrawler blockSpider = new BlockCrawler(maxPortalSize);
                    try {
                        ArrayList<Coords> blockCoordArray = new ArrayList<Coords>();
                        blockSpider.start(block, blockCoordArray);
                        // Test to see if blocks are already in a portal
                        Set<String> existingPortalOverlap = new HashSet<String>();
                        for (Coords coords : blockCoordArray) {
                            String overlapPortalName = mPDM.getPortalName(coords);
                            if (overlapPortalName != null)
                                existingPortalOverlap.add(overlapPortalName);
                        }
                        // If there is no portal-portal overlap
                        if (existingPortalOverlap.size() == 0) {
                            createPortal(player, block, portalCreate.portalName, portalCreate.tpCoords, portalCreate.blockType, blockCoordArray);
                        } else {
                            /*
                             * Alert player that the portal they are trying to
                             * create overlaps "..." portals.
                             */
                            player.sendMessage(mCC + "Portal \"" + portalCreate.portalName + "\" could not be created because it overlapped existing portals: "
                                    + existingPortalOverlap.toString() + ".");
                        }
                    } catch (MaxRecursionException e) {
                        /*
                         * Alert player that the portal they are trying to
                         * create has reached max recursion size
                         */
                        player.sendMessage(mCC + "Portal \"" + portalCreate.portalName
                                + "\" could not be created because it was larger than the max Portal size of " + String.valueOf(maxPortalSize) + ".");
                    }
                } else {
                    player.sendMessage(mCC + "There was an error using that Portal name. It wasn't a valid alpha-numeric string.");
                }
            } else {
                player.sendMessage("A Portal with the name \"" + portalCreate.portalName + "\" already exists.");
                mPTM.removeCreating(player.getUniqueId());
            }
        } else {
            player.sendMessage("The Portal should be made out of either Gold/Silver/Ender Portal/Portal Blocks originally");
        }
    }

    public void createPortal(CommandSender sender, Block block, String portalName, CoordsPY tpCoords, Material portalMaterial,
                             ArrayList<Coords> blockCoordsArray) {
        PortalInfo newPortalInfo = new PortalInfo();
        newPortalInfo.name = portalName;
        newPortalInfo.tpCoords = tpCoords;
        newPortalInfo.blockCoordArray = blockCoordsArray;

        /*
         * Trigger WarpPortalCreateEvent so that other plugins can tie in to new
         * WarpPortal creations
         */
        WarpPortalsCreateEvent wpCreateEvent = new WarpPortalsCreateEvent(sender, newPortalInfo);
        // Call WarpPortalsTeleportEvent
        Bukkit.getPluginManager().callEvent(wpCreateEvent);

        // Check if the WarpPortalCreateEvent has been cancelled
        if (!wpCreateEvent.isCancelled()) {
            PortalInfo createPortalInfo = wpCreateEvent.getPortal();
            Location loc = block.getLocation();
            /*
             * Update the blocks in the Portal to whatever the Player designated
             * them to be.
             */
            changeMaterial(portalMaterial, createPortalInfo.blockCoordArray, loc);
            // Add portal
            mPDM.addPortal(createPortalInfo.name, createPortalInfo);
            // Deactivate portal creation tool
            if (sender instanceof Player) {
                mPTM.removeCreating(((Player) sender).getUniqueId());
            }
            // Alert player of portal creation success
            sender.sendMessage(mCC + "\"" + createPortalInfo.name + "\" created and linked to " + createPortalInfo.tpCoords.toNiceString());
        } else {
            sender.sendMessage(mCC + "\"" + portalName + "\" creation has been cancelled by another plugin.");
        }
    }

    /**
     * Change the material of the block at each Coords in blockCoordArray.
     *
     * @param material
     *            - Material to change the block to. Must be a block.
     *            {@link Material#isBlock()}
     * @param blockCoordArray
     *            List<{@link Coords} of the blocks to update.
     * @param location
     *            {@link Location} to use for updating the blocks.
     * @return if the material can be used or not.
     */
    public boolean changeMaterial(Material material, List<Coords> blockCoordArray, Location location) {
        if (material.isBlock()) {
            for (Coords crd : blockCoordArray) {
                location.setX(crd.x);
                location.setY(crd.y);
                location.setZ(crd.z);
                location.getBlock().setType(material);
            }
            return true;
        }
        return false;
    }
}