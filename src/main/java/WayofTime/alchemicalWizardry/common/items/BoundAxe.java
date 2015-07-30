package WayofTime.alchemicalWizardry.common.items;

import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.block.BlockLeavesBase;
import net.minecraft.block.state.IBlockState;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemAxe;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.BlockPos;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import WayofTime.alchemicalWizardry.AlchemicalWizardry;
import WayofTime.alchemicalWizardry.api.items.interfaces.IBindable;
import WayofTime.alchemicalWizardry.common.ItemType;
import WayofTime.alchemicalWizardry.common.spell.complex.effect.SpellHelper;

import com.google.common.collect.HashMultiset;

public class BoundAxe extends ItemAxe implements IBindable
{
    public float efficiencyOnProperMaterial = 12.0F;
    public float damageVsEntity;
    private int energyUsed;

    public BoundAxe()
    {
        super(AlchemicalWizardry.bloodBoundToolMaterial);
        this.maxStackSize = 1;
        this.efficiencyOnProperMaterial = 12.0F;
        this.damageVsEntity = 5;
        setCreativeTab(AlchemicalWizardry.tabBloodMagic);
        setEnergyUsed(5);
    }

    public void setEnergyUsed(int i)
    {
        energyUsed = i;
    }

    public int getEnergyUsed()
    {
        return this.energyUsed;
    }

    @Override
    public void addInformation(ItemStack par1ItemStack, EntityPlayer par2EntityPlayer, List par3List, boolean par4)
    {
        par3List.add(StatCollector.translateToLocal("tooltip.boundaxe.desc"));

        if (!(par1ItemStack.getTagCompound() == null))
        {
            if (par1ItemStack.getTagCompound().getBoolean("isActive"))
            {
                par3List.add(StatCollector.translateToLocal("tooltip.sigil.state.activated"));
            } else
            {
                par3List.add(StatCollector.translateToLocal("tooltip.sigil.state.deactivated"));
            }

            if (!par1ItemStack.getTagCompound().getString("ownerName").equals(""))
            {
                par3List.add(StatCollector.translateToLocal("tooltip.owner.currentowner") + " " + par1ItemStack.getTagCompound().getString("ownerName"));
            }
        }
    }

    @Override
    public ItemStack onItemRightClick(ItemStack par1ItemStack, World world, EntityPlayer par3EntityPlayer)
    {
        if (!EnergyItems.checkAndSetItemOwner(par1ItemStack, par3EntityPlayer) || par3EntityPlayer.isSneaking())
        {
            this.setActivated(par1ItemStack, !getActivated(par1ItemStack));
            par1ItemStack.getTagCompound().setInteger("worldTimeDelay", (int) (world.getWorldTime() - 1) % 200);
            return par1ItemStack;
        }
        
        if (world.isRemote)
        {
            return par1ItemStack;
        }

        if (!getActivated(par1ItemStack) || SpellHelper.isFakePlayer(world, par3EntityPlayer))
        {
            return par1ItemStack;
        }

        if (par3EntityPlayer.isPotionActive(AlchemicalWizardry.customPotionInhibit))
        {
            return par1ItemStack;
        }
        
        if(!EnergyItems.syphonBatteries(par1ItemStack, par3EntityPlayer, 10000))
        {
        	return par1ItemStack;
        }

        BlockPos pos = par3EntityPlayer.getPosition();
        boolean silkTouch = EnchantmentHelper.getSilkTouchModifier(par3EntityPlayer);
        int fortuneLvl = EnchantmentHelper.getFortuneModifier(par3EntityPlayer);

        HashMultiset<ItemType> dropMultiset = HashMultiset.create();
        
        for (int i = -5; i <= 5; i++)
        {
            for (int j = 0; j <= 10; j++)
            {
                for (int k = -5; k <= 5; k++)
                {
                	BlockPos newPos = pos.add(i, j, k);
                	IBlockState state = world.getBlockState(newPos);
                    Block block = state.getBlock();

                    if (block != null)
                    {
                        float str = getStrVsBlock(par1ItemStack, block);

                        if (str > 1.1f || block instanceof BlockLeavesBase && world.canMineBlockBody(par3EntityPlayer, newPos))
                        {
                            if (silkTouch && block.canSilkHarvest(world, newPos, state, par3EntityPlayer))
                            {
                                dropMultiset.add(new ItemType(block, block.getMetaFromState(state)));
                            } else
                            {
                                List<ItemStack> itemDropList = block.getDrops(world, newPos, state, fortuneLvl);

                                if (itemDropList != null)
                                {
                                    for (ItemStack stack : itemDropList)
                                        dropMultiset.add(ItemType.fromStack(stack), stack.stackSize);
                                }
                            }

                            world.setBlockToAir(newPos);
                        }
                    }
                }
            }
        }

        BoundPickaxe.dropMultisetStacks(dropMultiset, world, pos.getX(), pos.getY() + par3EntityPlayer.getEyeHeight(), pos.getZ());

        return par1ItemStack;
    }

    @Override
    public void onUpdate(ItemStack par1ItemStack, World world, Entity par3Entity, int par4, boolean par5)
    {
        if (!(par3Entity instanceof EntityPlayer))
        {
            return;
        }

        EntityPlayer par3EntityPlayer = (EntityPlayer) par3Entity;

        if (par1ItemStack.getTagCompound() == null)
        {
            par1ItemStack.setTagCompound(new NBTTagCompound());
        }
        if (world.getWorldTime() % 200 == par1ItemStack.getTagCompound().getInteger("worldTimeDelay") && par1ItemStack.getTagCompound().getBoolean("isActive"))
        {
            if (!par3EntityPlayer.capabilities.isCreativeMode)
            {
                if(!EnergyItems.syphonBatteries(par1ItemStack, par3EntityPlayer, 20))
                {
                	this.setActivated(par1ItemStack, false);
                }
            }
        }

        par1ItemStack.setItemDamage(0);
    }

    public void setActivated(ItemStack stack, boolean newActivated)
    {
        stack.setItemDamage(newActivated ? 1 : 0);
    }

    public boolean getActivated(ItemStack stack)
    {
        return stack.getItemDamage() == 1;
    }

    /**
     * Returns the strength of the stack against a given block. 1.0F base, (Quality+1)*2 if correct blocktype, 1.5F if
     * sword
     */
    @Override
    public float getStrVsBlock(ItemStack par1ItemStack, Block par2Block)
    {
        if (!getActivated(par1ItemStack))
        {
            return 0.0F;
        }

        return super.getStrVsBlock(par1ItemStack, par2Block);
    }

    /**
     * Current implementations of this method in child classes do not use the entry argument beside ev. They just raise
     * the damage on the stack.
     */
    public boolean hitEntity(ItemStack par1ItemStack, EntityLivingBase par2EntityLivingBase, EntityLivingBase par3EntityLivingBase)
    {
        return getActivated(par1ItemStack);
    }

    public boolean onBlockDestroyed(ItemStack par1ItemStack, World world, Block par3, int par4, int par5, int par6, EntityLivingBase par7EntityLivingBase)
    {
        return true;
    }

    @SideOnly(Side.CLIENT)

    /**
     * Returns True is the item is renderer in full 3D when hold.
     */
    public boolean isFull3D()
    {
        return true;
    }

    /**
     * Return the enchantability factor of the item, most of the time is based on material.
     */
    @Override
    public int getItemEnchantability()
    {
        return 30;
    }

    /**
     * FORGE: Overridden to allow custom tool effectiveness
     */
    @Override
    public float getDigSpeed(ItemStack stack, IBlockState state)
    {
        if (!getActivated(stack))
        {
            return 0.0F;
        }

        for (String type : getToolClasses(stack))
        {
            if (state.getBlock().isToolEffective(type, state))
                return efficiencyOnProperMaterial;
        }
        return super.getDigSpeed(stack, state);
    }

    @Override
    public boolean onLeftClickEntity(ItemStack stack, EntityPlayer player, Entity entity)
    {
        return !getActivated(stack);
    }

    @Override
    public int getHarvestLevel(ItemStack stack, String toolClass)
    {
        if (getActivated(stack) && "axe".equals(toolClass))
        {
            return 5;
        }

        return 0;
    }
}
