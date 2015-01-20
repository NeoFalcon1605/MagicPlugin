package com.elmakers.mine.bukkit.spell;

import com.elmakers.mine.bukkit.api.action.GeneralAction;
import com.elmakers.mine.bukkit.api.action.BlockAction;
import com.elmakers.mine.bukkit.api.action.EntityAction;
import com.elmakers.mine.bukkit.api.magic.Mage;
import com.elmakers.mine.bukkit.api.magic.MageController;
import com.elmakers.mine.bukkit.api.spell.SpellResult;
import com.elmakers.mine.bukkit.utility.CompatibilityUtils;
import com.elmakers.mine.bukkit.utility.ConfigurationUtils;
import com.elmakers.mine.bukkit.utility.Target;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class ActionHandler
{
    private static final String ACTION_BUILTIN_CLASSPATH = "com.elmakers.mine.bukkit.action.builtin";

    private List<GeneralAction> generalActions = new ArrayList<GeneralAction>();
    private List<BlockAction> blockActions = new ArrayList<BlockAction>();
    private List<EntityAction> entityActions = new ArrayList<EntityAction>();

    private final ActionSpell spell;
    private boolean undoable = false;
    private boolean usesBrush = false;

    public ActionHandler(ActionSpell spell)
    {
        this.spell = spell;
    }

    public void load(ConfigurationSection root, String key)
    {
        undoable = false;
        usesBrush = false;
        Collection<ConfigurationSection> actionNodes = ConfigurationUtils.getNodeList(root, key);
        if (actionNodes != null)
        {
            for (ConfigurationSection actionConfiguration : actionNodes)
            {
                if (actionConfiguration.contains("class"))
                {
                    String actionClassName = actionConfiguration.getString("class");
                    try
                    {
                        if (!actionClassName.contains("."))
                        {
                            actionClassName = ACTION_BUILTIN_CLASSPATH + "." + actionClassName;
                        }
                        Class<?> genericClass = Class.forName(actionClassName);
                        if (!BaseSpellAction.class.isAssignableFrom(genericClass)) {
                            throw new Exception("Must extend SpellAction");
                        }

                        Class<? extends BaseSpellAction> actionClass = (Class<? extends BaseSpellAction>)genericClass;
                        BaseSpellAction action = actionClass.newInstance();
                        actionConfiguration.set("class", null);
                        if (actionConfiguration.getKeys(false).size() == 0) {
                            actionConfiguration = null;
                        }
                        action.load(spell, actionConfiguration);
                        usesBrush = usesBrush || action.usesBrush();
                        undoable = undoable || action.isUndoable();
                        if (action instanceof GeneralAction) {
                            generalActions.add((GeneralAction)action);
                        }
                        if (action instanceof EntityAction) {
                            entityActions.add((EntityAction)action);
                        }
                        if (action instanceof BlockAction) {
                            blockActions.add((BlockAction)action);
                        }
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }
            }
        }
    }

    public SpellResult perform(ConfigurationSection parameters)
    {
        return perform(parameters, null);
    }

    public SpellResult perform(ConfigurationSection parameters, Location targetLocation)
    {
        SpellResult result = SpellResult.CAST;
        for (GeneralAction generalAction : generalActions)
        {
            SpellResult actionResult = generalAction.perform(generalAction.getParameters(parameters));
            if (actionResult.ordinal() > result.ordinal())
            {
                result = actionResult;
            }
        }

        if (entityActions.size() == 0 && blockActions.size() == 0)
        {
            return result;
        }

        Entity targetedEntity = null;
        List<Entity> targetEntities = new ArrayList<Entity>();
        if (targetLocation == null)
        {
            Target target = spell.getTarget();
            if (!target.hasTarget())
            {
                return SpellResult.NO_TARGET;
            }
            if (target.hasEntity())
            {
                targetedEntity = target.getEntity();
                targetEntities.add(targetedEntity);
            }
            targetLocation = target.getLocation();
        }

        int radius = parameters.getInt("radius", 0);
        int coneCount = parameters.getInt("count", 0);
        Mage mage = spell.getMage();
        radius = (int)(mage.getRadiusMultiplier() * radius);

        if (radius > 0)
        {
            List<Entity> entities = CompatibilityUtils.getNearbyEntities(targetLocation, radius, radius, radius);
            for (Entity entity : entities)
            {
                if (entity != targetedEntity && entity != mage.getEntity() && spell.canTarget(entity))
                {
                    targetEntities.add(entity);
                }
            }
        }
        else if (coneCount > 1)
        {
            List<Target> entities = spell.getAllTargetEntities();
            for (int i = 1; i < coneCount && i < entities.size(); i++)
            {
                targetEntities.add(entities.get(i).getEntity());
            }
        }

        if (targetEntities.size() == 0)
        {
            return SpellResult.NO_TARGET;
        }

        for (Entity entity : targetEntities)
        {
            for (EntityAction action : entityActions)
            {
                SpellResult actionResult = action.perform(action.getParameters(parameters), entity);
                if (actionResult.ordinal() > result.ordinal())
                {
                    result = actionResult;
                }
            }
        }
        return result;
    }

    public boolean isUndoable()
    {
        return undoable;
    }

    public boolean usesBrush()
    {
        return usesBrush;
    }
}
