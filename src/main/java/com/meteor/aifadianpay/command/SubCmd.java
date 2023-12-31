package com.meteor.aifadianpay.command;

import com.google.common.collect.ImmutableMap;
import com.meteor.aifadianpay.util.BaseConfig;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class SubCmd {
    protected JavaPlugin plugin;
    public SubCmd(JavaPlugin plugin){
        this.plugin = plugin;
    }
    /**
     * 指令标签
     */
    public abstract String label();
    /**
     * 指令所需权限
     */
    public abstract String getPermission();
    /**
     * 是否仅玩家可使用
     */
    public abstract boolean playersOnly();
    public abstract String usage();
    public List<String> getTab(final Player p, final int i, final String[] args) {
        return Collections.emptyList();
    }
    public abstract void perform(final CommandSender p0, final String[] p1);
    public void execute(CommandSender sender, String[] args){
        if(this.playersOnly()&&!(sender instanceof Player)){
            sender.sendMessage(BaseConfig.STORE.getMessageBox().getMessage(null,"message.only-player"));
            return;
        }
        if (!hasPerm(sender)) {
            Map<String,String> prams = new HashMap<>();
            prams.put("@permission@",getPermission());
            sender.sendMessage(BaseConfig.STORE.getMessageBox().getMessage(prams,"message.no-perm"));
            return;
        }
        this.perform(sender,args);
    }
    public boolean hasPerm(CommandSender sender) {
        if (this.getPermission() == null) {
            return true;
        }
        if (sender instanceof Player) {
            final Player p = (Player)sender;
            return p.hasPermission(this.getPermission());
        }
        return true;
    }
}
