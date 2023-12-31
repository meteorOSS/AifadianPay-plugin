package com.meteor.aifadianpay.storage.sub;

import com.meteor.aifadianpay.AifadianPay;
import com.meteor.aifadianpay.afdian.response.Order;
import com.meteor.aifadianpay.afdian.response.SkuDetail;
import com.meteor.aifadianpay.api.event.SendOutGoodsEvent;
import com.meteor.aifadianpay.data.ShopItem;
import com.meteor.aifadianpay.filter.FilterManager;
import com.meteor.aifadianpay.storage.AbstractStorage;
import com.meteor.aifadianpay.storage.export.ExportOrder;
import com.meteor.aifadianpay.storage.export.ExportSkuDetail;
import com.meteor.aifadianpay.util.BaseConfig;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.sql.*;
import java.util.*;

public class SqliteStorage extends AbstractStorage {
    private AifadianPay plugin;
    private Connection connection;


    private void connect(){
        try {
            Class.forName("org.sqlite.JDBC");
            this.connection = DriverManager.getConnection("jdbc:sqlite:"+plugin.getDataFolder().getPath()+"/data.db");

            String orders_column = "CREATE TABLE IF NOT EXISTS AIFADIAN_ORDERS_0 ("
                    + "out_trade_no varchar(40) PRIMARY KEY,"
                    + "remark varchar(100),"
                    + "user_id varchar(40),"
                    + "plan_title varchar(40),"
                    + "redeem_id varchar(40),"
                    + "price varchar(40),"
                    + "insert_time bigint"
                    + ")";

            Statement statement = connection.createStatement();

            String skudetail_column = "CREATE TABLE IF NOT EXISTS AIFADIAN_SKUDETAIL_0 ("
                    + "out_trade_no varchar(40),"
                    + "sku_id varchar(40),"
                    + "price varchar(40),"
                    + "name varchar(40),"
                    + "count int"
                    + ")";
            statement.execute(orders_column);
            statement.execute(skudetail_column);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
        try {
            connection.setAutoCommit(false);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }


    public SqliteStorage(AifadianPay plugin){
        this.plugin = plugin;
        this.connect();;
    }

    public boolean isExistOrder(String no){
        PreparedStatement preparedStatement = null;
        ResultSet resultSet;
        resultSet = null;
        try {
            preparedStatement = this.connection.prepareStatement("select * from " + ORDER_TABLE + " where out_trade_no = ?");
            preparedStatement.setString(1,no);
            resultSet = preparedStatement.executeQuery();
            while (resultSet.next()) return true;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }finally {
            try {
                preparedStatement.close();
                resultSet.close();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }
        return false;
    }


    public void insertOrder(Order order) {

        String sql = "INSERT INTO @table@ (out_trade_no, remark, user_id, plan_title, redeem_id,price,insert_time) VALUES (?, ?, ?, ?,?,?,?)"
                .replace("@table@",ORDER_TABLE);

        try (
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1,order.getOutTradeNo());
            statement.setString(2,order.getRemark());
            statement.setString(3,order.getUserId());
            statement.setString(4,order.getPlanTitle());
            statement.setString(5,order.getRedeemId());
            statement.setString(6,order.getTotalAmount());
            statement.setLong(7,System.currentTimeMillis());
            statement.executeUpdate();
        } catch (SQLException e) {
        }
    }


    public void insertSkudetail(Order order,SkuDetail skuDetail) {

        String sql = "INSERT INTO @table@ (out_trade_no, sku_id, price, `name`, `count`) VALUES (?, ?, ?, ?, ?)"
                .replace("@table@",SKU_DETAIL_TABLE);

        try (
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1,order.getOutTradeNo());
            statement.setString(2,skuDetail.getSkuId());
            statement.setString(3,order.getTotalAmount());
            statement.setString(4,skuDetail.getName());
            statement.setInt(5,skuDetail.getCount());
            statement.executeUpdate();
        } catch (SQLException e) {
        }
    }

    @Override
    public boolean handeOrder(Order order, boolean b) {
        List<Order> orders = FilterManager.meet(Arrays.asList(order));
        if(!orders.isEmpty()){
            Order handleOrder = orders.get(0);
            Player playerExact = Bukkit.getPlayerExact(handleOrder.getRemark());

            if(playerExact!=null&&b){
                try {
                    if(!isExistOrder(handleOrder.getOutTradeNo())){
                        /***
                         * 插入已处理订单表
                         */
                        this.insertOrder(handleOrder);
                        /***
                         * 型号处理
                         */
                        for (SkuDetail skuDetail : handleOrder.getSkuDetail()) {
                            this.insertSkudetail(handleOrder,skuDetail);
                        }
                        /**
                         * 发货
                         */
                        ShopItem shopItem = BaseConfig.STORE.getShopItemMap().get(handleOrder.getPlanTitle());
                        Bukkit.getScheduler().runTask(plugin,()->{
                            SendOutGoodsEvent sendOutGoodsEvent = new SendOutGoodsEvent(playerExact,shopItem,handleOrder.getOutTradeNo(),handleOrder.getSkuDetail(),handleOrder);
                            Bukkit.getServer().getPluginManager().callEvent(sendOutGoodsEvent);
                        });
                        connection.commit();
                        return true;
                    }
                }catch (SQLException sqlException){

                    try {
                        connection.rollback();
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }

                }


            }
        }

        return false;
    }

    @Override
    public int queryPlayerDonate(String p) {
        PreparedStatement preparedStatement = null;
        ResultSet resultSet = null;
        try {
            String sql = "select sum(`price`) as count from "+ORDER_TABLE+" where remark = ?";
            preparedStatement = connection.prepareStatement(sql);
            preparedStatement.setString(1,p);
            resultSet = preparedStatement.executeQuery();
            while (resultSet.next()) return resultSet.getInt("sum");
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }finally {
            try {
                preparedStatement.close();
                resultSet.close();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }
        return 0;
    }

    @Override
    public boolean isHandleOrder(String tradeNo) {
        return isExistOrder(tradeNo);
    }


    public void insertData(String tableName, Map<String, Object> data) {
        StringJoiner columns = new StringJoiner(", ", "(", ")");
        StringJoiner params = new StringJoiner(", ", "(", ")");
        for (String column : data.keySet()) {
            columns.add("`" + column + "`");
            params.add("?");
        }
        String sql = "INSERT INTO " + tableName + " " + columns.toString() + " VALUES " + params.toString();

        try (
             PreparedStatement statement = getConnection().prepareStatement(sql)) {

            int index = 1;
            for (Object value : data.values()) {
                statement.setObject(index++, value);
            }

            statement.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }


    @Override
    public void importData(List<ExportOrder> exportOrders, List<ExportSkuDetail> skuDetails) {
        Map<String,Object> objectMap = new HashMap<>();
        for (ExportOrder exportOrder : exportOrders) {
            Order order = exportOrder.getOrder();
            objectMap.put("out_trade_no",order.getOutTradeNo());
            objectMap.put("remark",order.getRemark());
            objectMap.put("user_id",order.getUserId());
            objectMap.put("plan_title",order.getPlanTitle());
            objectMap.put("redeem_id",order.getRedeemId());
            objectMap.put("price",order.getTotalAmount());
            objectMap.put("insert_time",exportOrder.getInsertTime());
            insertData(ORDER_TABLE,objectMap);
        }

        objectMap.clear();

        for (ExportSkuDetail skuDetail : skuDetails) {
            objectMap.put("out_trade_no",skuDetail.getOut_trade_no());
            objectMap.put("sku_id",skuDetail.getSku_id());
            objectMap.put("price",skuDetail.getPrice());
            objectMap.put("name",skuDetail.getName());
            objectMap.put("count",skuDetail.getCount());
            insertData(SKU_DETAIL_TABLE,objectMap);
        }
    }

    @Override
    public AifadianPay getPlugin() {
        return plugin;
    }

    @Override
    public Connection getConnection() {
        return connection;
    }

}
