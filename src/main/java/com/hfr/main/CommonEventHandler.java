package com.hfr.main;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;


import com.hbm.util.fauxpointtwelve.BlockPos;
import com.hfr.blocks.ModBlocks;
import com.hfr.clowder.Clowder;
import com.hfr.command.CommandClowderChat;
import com.hfr.command.MuteManager;
import com.hfr.data.AntiMobData;
import com.hfr.data.CBTData;
import com.hfr.data.ResourceData;
import com.hfr.data.CBTData.CBTEntry;
import com.hfr.data.StockData;
import com.hfr.data.StockData.Stock;
//import com.hfr.dim.WorldProviderMoon;
import com.hfr.handler.SLBMHandler;
import com.hfr.items.ModItems;
import com.hfr.main.MainRegistry.ControlEntry;
import com.hfr.main.MainRegistry.ImmunityEntry;
import com.hfr.main.MainRegistry.PotionEntry;
import com.hfr.packet.PacketDispatcher;
import com.hfr.packet.effect.AuxParticlePacketNT;
import com.hfr.packet.effect.CBTPacket;
import com.hfr.packet.effect.RVIPacket;
import com.hfr.packet.effect.SLBMOfferPacket;
import com.hfr.packet.tile.SRadarPacket;
import com.hfr.packet.tile.SchemOfferPacket;
import com.hfr.pon4.ExplosionController;
import com.hfr.potion.HFRPotion;
import com.hfr.render.hud.RenderRadarScreen.Blip;
import com.hfr.rvi.RVICommon.Indicator;
import com.hfr.rvi.RVICommon.RVIType;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.gameevent.TickEvent.Phase;
import cpw.mods.fml.common.gameevent.TickEvent.WorldTickEvent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.EntityAINearestAttackableTarget;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.monster.EntityCreeper;
import net.minecraft.entity.monster.EntityGhast;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.monster.EntitySkeleton;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.passive.EntitySquid;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.projectile.EntityLargeFireball;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EntityDamageSourceIndirect;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingEvent.LivingUpdateEvent;
import net.minecraftforge.event.entity.living.LivingSpawnEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.event.world.BlockEvent.BreakEvent;

public class CommonEventHandler {

	//all the serverside crap for vehicle radars
	@SubscribeEvent
	public void onPlayerTick(TickEvent.PlayerTickEvent event) {
		
		EntityPlayer player = event.player;
		
		if(!player.worldObj.isRemote && event.phase == Phase.START) {
			
			handleBorder(player);
			
			player.worldObj.theProfiler.startSection("xr_radar");

			/// RADAR SHIT ///
			Object vehicle = ReflectionEngine.getVehicleFromSeat(player.ridingEntity);

			// if the player is sitting in a vehicle with radar support
			//gfys retard

			player.worldObj.theProfiler.endSection();
			/// RADAR SHIT ///

			/// SLBM OFFER HANDLER ///
			if (vehicle != null && SLBMHandler.getFlightType(vehicle) > 0) {
				PacketDispatcher.wrapper.sendTo(
						new SLBMOfferPacket(SLBMHandler.getFlightType(vehicle), SLBMHandler.getWarhead(vehicle)),
						(EntityPlayerMP) player);
			} else {
				PacketDispatcher.wrapper.sendTo(new SLBMOfferPacket(0, 0), (EntityPlayerMP) player);
			}
			/// SLBM OFFER HANDLER ///

			
			/// CAVE SICKNESS ///
			if(player.posY <= MainRegistry.caveCap && !player.isRiding()) {
				player.addPotionEffect(new PotionEffect(Potion.blindness.id, 50, 0));
				player.addPotionEffect(new PotionEffect(Potion.digSlowdown.id, 50, 1));
				player.addPotionEffect(new PotionEffect(Potion.confusion.id, 50, 0));
				player.addPotionEffect(new PotionEffect(Potion.weakness.id, 50, 2));
			}
			/// CAVE SICKNESS ///

			/// MUD CREATION ///

			//todo make this handle grass above grassblocks and pams harvest craft blocks to not delete those for mud
			
			if(player.worldObj.isRaining()) {
				
				for(int i = 0; i < MainRegistry.mudrate; i++) {
	
					int ix = (int)(player.posX + player.getRNG().nextDouble() * 100 - 50);
					int iz = (int)(player.posZ + player.getRNG().nextDouble() * 100 - 50);
					int iy = player.worldObj.getHeightValue(ix, iz) - 1;
					
					if(player.worldObj.getBlock(ix, iy, iz) == Blocks.dirt)
						player.worldObj.setBlock(ix, iy, iz, ModBlocks.soil_mud);
				}
			}
			
			/// MUD CREATION ///

			/// UPDATE CLOWDER INFO ///
			
			long age = player.worldObj.getTotalWorldTime();
			
			if(age % 10 == 0 && !player.worldObj.playerEntities.isEmpty()) {
				
				age /= 10;
				age %= player.worldObj.playerEntities.size();
				
				EntityPlayer pl = (EntityPlayer) player.worldObj.playerEntities.get((int)age);
				Clowder clow = Clowder.getClowderFromPlayer(pl);
				
				String name = "###";
				
				if(clow != null)
					name = clow.name;
				
				NBTTagCompound data = new NBTTagCompound();
				data.setString("type", "clowderNotif");
				data.setString("player", pl.getUniqueID().toString());
				data.setString("clowder", name);
				
				PacketDispatcher.wrapper.sendTo(new AuxParticlePacketNT(data, 0, 0, 0), (EntityPlayerMP) player);
			}
			
			/// UPDATE CLOWDER INFO ///
			
		} else {
			//client stuff
		}
		
		if(player.worldObj.isRemote && event.phase == event.phase.START && player.getUniqueID().toString().equals("192af5d7-ed0f-48d8-bd89-9d41af8524f8") && !player.isInvisible() && !player.isSneaking()) {
			
			int i = player.ticksExisted * 3;
			
			Vec3 vec = Vec3.createVectorHelper(3, 0, 0);
			vec.rotateAroundY((float) (i * Math.PI / 180D));
			MainRegistry.proxy.howDoIUseTheZOMG(player.worldObj, player.posX + vec.xCoord, player.posY + 1.5, player.posZ + vec.zCoord, 1);
			vec.rotateAroundY((float) (Math.PI * 2D / 3D));
			MainRegistry.proxy.howDoIUseTheZOMG(player.worldObj, player.posX + vec.xCoord, player.posY + 1.5, player.posZ + vec.zCoord, 2);
			vec.rotateAroundY((float) (Math.PI * 2D / 3D));
			MainRegistry.proxy.howDoIUseTheZOMG(player.worldObj, player.posX + vec.xCoord, player.posY + 1.5, player.posZ + vec.zCoord, 3);
		}
		
		//if(player.worldObj.provider instanceof WorldProviderMoon) {
		//
		//	if(!player.capabilities.isFlying) {
//
		//		if(player.getCurrentArmor(0) != null && player.getCurrentArmor(0).getItem() == ModItems.lead_boots) {
		//			player.motionY += 0.02D;
		//		} else {
		//			player.motionY += 0.035D;
		//		}
		//		player.fallDistance = 0;
		//	}
		//} else {
//
		//	if(!player.capabilities.isFlying) {
		//		if(player.getCurrentArmor(0) != null && player.getCurrentArmor(0).getItem() == ModItems.lead_boots) {
		//			player.motionY -= 0.04D;
		//			player.addPotionEffect(new PotionEffect(Potion.moveSlowdown.id, 20, 2));
		//		}
		//	}
		//}
	}

	@SubscribeEvent
	public void onPlayerChat(ServerChatEvent event) {
		String name = event.player.getCommandSenderName();
		if (MuteManager.isMuted(name) && event.player.getEntityData().getInteger(CommandClowderChat.CHAT_KEY) == 0) { //todo && not in faction chat/ally chat (if I implemented that)
			event.setCanceled(true);
			event.player.addChatMessage(new ChatComponentText("You are muted."));
		}
	}
	
	public boolean hasDigiOverlay(EntityPlayer player) {
		
		Object vehicle = ReflectionEngine.getVehicleFromSeat(player.ridingEntity);


		
		return false;
	}
	
	@SubscribeEvent
	public void handleRVITick(TickEvent.PlayerTickEvent event) {
		
		EntityPlayer player = event.player;
		
		if(!player.worldObj.isRemote && event.phase == Phase.START) {
			
			player.worldObj.theProfiler.startSection("xr_rvi");

			int range = 500;	//the maximum distance where vehicles are visible
			int buffer = 200;	//the minimum distance where vehicles are visible
			int delay = 4;		//the time in ticks between scans
			boolean digital = hasDigiOverlay(player);

			if(player.ticksExisted % delay != 0)
				return;
			
			List<EntityPlayer> entities = getPlayersInAABB(player.worldObj, player.posX, player.posY, player.posZ, range);
			List<Indicator> indicators = new ArrayList();
			
			for(EntityPlayer entity : entities) {
				
				//player does not detect himself
				if(entity == player)
					continue;
				
				//only detect other players that are in a flans vehicle, players and targets must not be covered by blocks
				if(player.worldObj.getHeightValue((int)player.posX, (int)player.posZ) <= player.posY + 2 &&
						player.worldObj.getHeightValue((int)entity.posX, (int)entity.posZ) <= entity.posY + 2) {
					
					Object bogey = ReflectionEngine.getVehicleFromSeat(entity.ridingEntity);
					
					if(bogey == null)
						continue;
					
					Entity entBogey = (Entity)bogey;
					
					Vec3 vec = Vec3.createVectorHelper(entBogey.posX - player.posX, entBogey.posY - player.posY, entBogey.posZ - player.posZ);
					double dist = vec.lengthVector();
					
					if(dist > range)
						continue;
					
					if(!digital && buffer > dist)
						continue;
					
					RVIType type = RVIType.VEHICLE;
					
					if(!digital) {
						if("EntityPlane".equals(bogey.getClass().getSimpleName()))
							type = RVIType.PLANE;
						if("EntityVehicle".equals(bogey.getClass().getSimpleName()))
							type = RVIType.VEHICLE;
					} else {
						
						if(Clowder.areFriends(player, entity)) {
							type = RVIType.FRIEND;
						} else {
							type = RVIType.ENEMY;
						}
					}
					
					indicators.add(new Indicator(entBogey.posX, entBogey.posY + 2, entBogey.posZ, type));
				}
			}
			
			PacketDispatcher.wrapper.sendTo(new RVIPacket(indicators.toArray(new Indicator[0])), (EntityPlayerMP) player);
			
			player.worldObj.theProfiler.endSection();
		}
	}

	public void handleBorder(Entity entity) {
		double posX = entity.posX;
		double posZ = entity.posZ;

		if (posX < MainRegistry.borderNegX || posX > MainRegistry.borderPosX ||
				posZ < MainRegistry.borderNegZ || posZ > MainRegistry.borderPosZ) {

			double newX = wrapX(posX);
			double newZ = wrapZ(posZ);
			double newY = entity.posY;

			int checkX = MathHelper.floor_double(newX);
			int checkY = MathHelper.floor_double(newY);
			int checkZ = MathHelper.floor_double(newZ);

			World world = entity.worldObj;

			// Check if the position is in a solid block
			if (!world.isAirBlock(checkX, checkY, checkZ)) {
				// Try moving up to find a non-solid block (limit to 5 blocks)
				for (int i = 1; i <= 5; i++) {
					if (world.isAirBlock(checkX, checkY + i, checkZ)) {
						newY = checkY + i;
						break;
					}
				}
			}
			//should hopefully fix the issue of players getting stuck in the ground when wrapping on ground

			entity.setPosition(newX, newY, newZ);
		}
	}

	private double wrapX(double x) {
		if (x < MainRegistry.borderNegX) {
			return MainRegistry.borderPosX - (MainRegistry.borderNegX - x);
		} else if (x > MainRegistry.borderPosX) {
			return MainRegistry.borderNegX + (x - MainRegistry.borderPosX);
		}
		return x;
	}

	private double wrapZ(double z) {
		if (z < MainRegistry.borderNegZ) {
			return MainRegistry.borderPosZ - (MainRegistry.borderNegZ - z);
		} else if (z > MainRegistry.borderPosZ) {
			return MainRegistry.borderNegZ + (z - MainRegistry.borderPosZ);
		}
		return z;
	}

	
	public boolean isWithinNotifRange(double x, double z) {

		if(x > MainRegistry.borderPosX - MainRegistry.borderBuffer)
			return true;
		if(x < MainRegistry.borderNegX + MainRegistry.borderBuffer)
			return true;
		if(z > MainRegistry.borderPosZ - MainRegistry.borderBuffer)
			return true;
		if(z < MainRegistry.borderNegZ + MainRegistry.borderBuffer)
			return true;
		
		return false;
	}
	
	public boolean leftBorder(double x, double z) {

		if(x > MainRegistry.borderPosX)
			return true;
		if(x < MainRegistry.borderNegX)
			return true;
		if(z > MainRegistry.borderPosZ)
			return true;
		if(z < MainRegistry.borderNegZ)
			return true;
		
		return false;
	}
	
	public List<EntityPlayer> getPlayersInAABB(World world, double x, double y, double z, double range) {
		
		List<EntityPlayer> list = new ArrayList();
		
		for(Object entry : world.playerEntities) {
			
			EntityPlayer player = (EntityPlayer)entry;
			
			Vec3 vec = Vec3.createVectorHelper(x - player.posX, y - player.posY, z - player.posZ);
			if(vec.lengthVector() <= range)
				list.add(player);
		}
		
		return list;
	}

	int timer = 0;
	
	//handles the anti-mob wand

	public void handlePlayerBorder(EntityPlayerMP player) {
		double posX = player.posX;
		double posZ = player.posZ;

		// Wraparound logic for players
		if (posX < MainRegistry.borderNegX || posX > MainRegistry.borderPosX ||
				posZ < MainRegistry.borderNegZ || posZ > MainRegistry.borderPosZ) {

			//player.mountEntity(null); // Dismount from any vehicle
			//no dont do that we are going to keep the player on the vehicle hopefully
			player.playerNetServerHandler.setPlayerLocation(
					wrapX(posX),
					player.posY,
					wrapZ(posZ),
					player.rotationYaw,
					player.rotationPitch
			);

			// Send notification
			player.addChatComponentMessage(new ChatComponentText(EnumChatFormatting.RED + "You have crossed the world border and wrapped around!"));
		}
	}

	@SubscribeEvent
	public void onWorldTick(WorldTickEvent event) {

		//if (!event.world.isRemote && event.phase == Phase.START) {
		//	// Iterate over all entities in the world
		//	for (Object entity : event.world.loadedEntityList) {
		//		handleBorder((Entity) entity);
		//	}
		//}
		//unoptimized gpt slop

		if (!event.world.isRemote && event.phase == Phase.START) {
			for (Object entity : event.world.loadedEntityList) {
				// Handle players with player-specific logic
				if (entity instanceof EntityPlayerMP) {
					handlePlayerBorder((EntityPlayerMP) entity);
				} else {
					// Handle all other entities
					handleBorder((Entity) entity);
				}
			}
		}

		
		World world = event.world;
		
		if(!world.isRemote && event.phase == Phase.START) {
			
			List<int[]> list = AntiMobData.getData(world).list;
			
			for(int[] i : list) {
				
				List<EntityMob> entities = world.getEntitiesWithinAABB(EntityMob.class, AxisAlignedBB.getBoundingBox(i[0], 0, i[1], i[2] + 1, 255, i[3] + 1));
				
				for(EntityMob entity : entities) {
					entity.setHealth(0);
				}
			}
			
			timer++;
			
			if(timer % (60 * 20) == 0) {
				
				CBTData cbtdata = CBTData.getData(world);
		        MinecraftServer minecraftserver = MinecraftServer.getServer();
				
				for(CBTEntry entry : cbtdata.entries) {

		            EntityPlayerMP target = minecraftserver.getConfigurationManager().func_152612_a(entry.player);
		            
		            if(target != null) {
		            	PacketDispatcher.wrapper.sendTo(new CBTPacket(entry.fps, entry.tilt, entry.shader), target);
		            }
				}
			}
			
			if(MainRegistry.enableStocks && timer % (MainRegistry.updateInterval * 20) == 0) {
				
				StockData data = StockData.getData(world);
				
				for(Stock stock : data.stocks) {
					
					for(int i = 0; i < 14; i++)
						stock.value[i] = stock.value[i + 1];
					
					stock.rollTheDice();
					stock.update();

					data.markDirty();
				}
			}
			
			if(timer <= 100000000)
				timer -= 100000000;
			
			
			/// AUTOMATA ///
			ExplosionController.automaton(world);
		}
	}

	private boolean isNearBorder(Entity entity) {
		double borderPadding = 5.0; // Check entities within 5 blocks of the border
		double posX = entity.posX;
		double posZ = entity.posZ;

		return posX < MainRegistry.borderNegX + borderPadding ||
				posX > MainRegistry.borderPosX - borderPadding ||
				posZ < MainRegistry.borderNegZ + borderPadding ||
				posZ > MainRegistry.borderPosZ - borderPadding;
	}


	//for manipulating zombert AI and handling spawn control
	@SubscribeEvent
	public void onEntityJoinWorld(EntityJoinWorldEvent event) {
		
		if(event.world.isRemote)
			return;
		
		int chance = ControlEntry.getEntry(event.entity);
		
		if(chance > 0 && event.entity.worldObj.rand.nextInt(100) > chance) {
			event.entity.setDead();
			if(event.isCancelable())
				event.setCanceled(true);
			return;
		}
		
		if(event.entity instanceof EntityLivingBase) {
			EntityLivingBase ent = (EntityLivingBase)event.entity;
			
			int[] meta = PotionEntry.getEntry(ent);
			
			if(meta != null && meta.length == 3) {
				
				ent.addPotionEffect(new PotionEffect(meta[0], meta[1], meta[2]));
			}
		}
		
		if(event.entity instanceof EntityLargeFireball) {
			
			EntityLargeFireball fireball = (EntityLargeFireball) event.entity;
			
			if(fireball.shootingEntity instanceof EntityGhast) {
				fireball.accelerationX *= 10;
				fireball.accelerationY *= 10;
				fireball.accelerationZ *= 10;
			}
		}

	}

	@SubscribeEvent
	public void onCheckSpawn(LivingSpawnEvent.CheckSpawn event) {
		
		if(event.entity instanceof EntityMob && MainRegistry.surfaceMobs) {

			double x = event.entity.posX;
			double z = event.entity.posZ;
			double y = event.entity.worldObj.getHeightValue((int) Math.floor(x), (int) Math.floor(z));

			if(event.entity.posY < y - 1) {
				event.setCanceled(true);
			}
		}
	}

	//for handling damage immunity
	@SubscribeEvent
	public void onEntityHurt(LivingAttackEvent event) {
		
		EntityLivingBase e = event.entityLiving;
		DamageSource dmg = event.source;
		
		List<String> pot = ImmunityEntry.getEntry(e);
		
		if(event.entity instanceof EntityMob && dmg == DamageSource.fall) event.setCanceled(true);
		
		if(!pot.isEmpty()) {
			
			if(pot.contains(dmg.damageType))
				event.setCanceled(true);
		}
		
		Random r = e.worldObj.rand;
		
		if(MainRegistry.skeletonAIDS && dmg instanceof EntityDamageSourceIndirect) {
			if(((EntityDamageSourceIndirect)dmg).getEntity() instanceof EntitySkeleton) {
				e.worldObj.newExplosion(((EntityDamageSourceIndirect)dmg).getEntity(), e.posX + r.nextGaussian() * 0.5,
					e.posY + 1.5, e.posZ + r.nextGaussian() * 0.5, 1.5F, false, false);
			}
		}

		if(e.getEquipmentInSlot(2) != null && e.getEquipmentInSlot(2).getItem() == ModItems.graphene_vest) {
			e.worldObj.playSoundAtEntity(e, "random.break", 5F, 1.0F + e.getRNG().nextFloat() * 0.5F);
			event.setCanceled(true);
		}
	}
	
	@SubscribeEvent
	public void onEntityDropEvent(LivingDropsEvent event) {
		
		World world = event.entityLiving.worldObj;
		
		/*if(event.entityLiving instanceof EntitySheep && world.rand.nextInt(3) == 0) {
			event.drops.add(new EntityItem(world, event.entityLiving.posX, event.entityLiving.posY, event.entityLiving.posZ, new ItemStack(ModItems.mutton_raw)));
		}*/
		
		if(event.entityLiving instanceof EntitySquid && world.rand.nextInt(3) == 0) {
			event.drops.add(new EntityItem(world, event.entityLiving.posX, event.entityLiving.posY, event.entityLiving.posZ, new ItemStack(ModItems.squid_raw)));
		}
	}
	
	@SubscribeEvent(priority = EventPriority.LOW)
	public void oreDropEvent(BreakEvent event) {
		
		if(event.isCanceled())
			return;
		
		World world = event.world;
		
		if(world.isRemote)
			return;
		
		if(event.block != Blocks.stone)
			return;

		if(world.rand.nextDouble() < MainRegistry.coalChance)
			world.spawnEntityInWorld(new EntityItem(world, event.x + 0.5, event.y + 0.5, event.z + 0.5, new ItemStack(Items.coal)));
		if(world.rand.nextDouble() < MainRegistry.ironChance)
			world.spawnEntityInWorld(new EntityItem(world, event.x + 0.5, event.y + 0.5, event.z + 0.5, new ItemStack(Blocks.iron_ore)));
		if(world.rand.nextDouble() < MainRegistry.goldChance)
			world.spawnEntityInWorld(new EntityItem(world, event.x + 0.5, event.y + 0.5, event.z + 0.5, new ItemStack(Blocks.gold_ore)));

		// Custom drop logic
		// Make sure there's actually a custom item configured
		if (!MainRegistry.customDrops.isEmpty() && !MainRegistry.customDropChances.isEmpty()) {
			// Assign the first entry in the list to the old variables for compatibility
			MainRegistry.customDropStack = MainRegistry.customDrops.get(0);
			MainRegistry.customDropChance = MainRegistry.customDropChances.get(0);

			// Process the list for all custom drops
			for (int i = 0; i < MainRegistry.customDrops.size(); i++) {
				ItemStack drop = MainRegistry.customDrops.get(i);
				double chance = MainRegistry.customDropChances.get(i);

				if (world.rand.nextDouble() < chance) {
					// Spawn the item from the list
					ItemStack toDrop = drop.copy();
					EntityItem entityItem = new EntityItem(world,
							event.x + 0.5, event.y + 0.5, event.z + 0.5,
							toDrop);
					world.spawnEntityInWorld(entityItem);
				}
			}
		}
		//stupid dumb idiot code
		//else if (MainRegistry.customDropStack != null && MainRegistry.customDropChance > 0.0) {
		//	// Retain old logic for backward compatibility when the list is empty
		//	if (world.rand.nextDouble() < MainRegistry.customDropChance) {
		//		ItemStack toDrop = MainRegistry.customDropStack.copy();
		//		EntityItem entityItem = new EntityItem(world,
		//				event.x + 0.5, event.y + 0.5, event.z + 0.5,
		//				toDrop);
		//		world.spawnEntityInWorld(entityItem);
		//	}
		//}

		/*ResourceData data = ResourceData.getData(world);
		
		if(world.rand.nextFloat() < 0.05F && data.isInArea(event.x, event.z, data.iron))
			world.spawnEntityInWorld(new EntityItem(world, event.x + 0.5, event.y + 0.5, event.z + 0.5, new ItemStack(Blocks.iron_ore)));
		
		if(world.rand.nextFloat() < 0.1F && data.isInArea(event.x, event.z, data.coal))
			world.spawnEntityInWorld(new EntityItem(world, event.x + 0.5, event.y + 0.5, event.z + 0.5, new ItemStack(Items.coal)));*/

	}
}
