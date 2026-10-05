/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.quanlymayphatdien.g1.dal;

import com.quanlymayphatdien.g1.entity.Inventory;
import com.quanlymayphatdien.g1.entity.Receipt;
import com.quanlymayphatdien.g1.entity.ReceiptDetail;
import com.quanlymayphatdien.g1.entity.StockCard;
import com.quanlymayphatdien.g1.utils.GlobalUtils;
import java.sql.*;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 *
 * @author FPTShop
 */
public class ReceiptDAO extends DBContext implements I_DAO<Receipt> {

    public String generateReceiptCode(String type) {
        String prefix = "IMPORT".equals(type) ? "RX-IM" : "RX-EX";
        String date = LocalDateTime.now().toString().substring(0, 10).replace("-", "");
        String suffix = String.format("%03d", System.currentTimeMillis() % 1000);
        return prefix + "-" + date + "-" + suffix;
    }

    public List<Receipt> findWithFilters(String typeFilter, String statusFilter, String whFilter,
            String search, Integer createdByFilter, String relatedTypeFilter, int page, int pageSize, Integer currentUserId) {
        List<Receipt> allReceipts = new ArrayList<>();
        String sql = "SELECT r.*, w.name AS warehouse_name, "
                + "u1.name AS created_by_name, u2.name AS approved_by_name, "
                + "so.order_code, liq.liquidation_code, liq.liquidation_id, c.name AS customer_name, cr.name AS reason_name, "
                + "po.po_code AS purchase_order_code, "
                + "tr.transfer_code AS transfer_code, "
                + "rexp.receipt_code AS related_export_receipt_code "
                + "FROM receipt r "
                + "LEFT JOIN warehouse w ON r.warehouse_id = w.warehouse_id "
                + "LEFT JOIN user u1 ON r.created_by = u1.id "
                + "LEFT JOIN user u2 ON r.approved_by = u2.id "
                + "LEFT JOIN sale_order so ON r.order_id = so.order_id "
                + "LEFT JOIN purchase_order po ON r.purchase_order_id = po.po_id "
                + "LEFT JOIN liquidation liq ON r.liquidation_id = liq.liquidation_id "
                + "LEFT JOIN customer c ON so.customer_id = c.id OR liq.customer_id = c.id "
                + "LEFT JOIN category cr ON r.reason_id = cr.id "
                + "LEFT JOIN transfer tr ON r.linked_transfer_id = tr.transfer_id "
                + "LEFT JOIN receipt rexp ON r.related_export_receipt_id = rexp.receipt_id "
                + "WHERE 1=1 ";
        List<Object> inputs = new ArrayList<>();
        if (typeFilter != null && !typeFilter.isEmpty()) {
            sql += "AND r.receipt_type = ? ";
            inputs.add(typeFilter);
        }
        if (statusFilter != null && !statusFilter.isEmpty()) {
            sql += "AND r.status = ? ";
            inputs.add(statusFilter);
        }
        if (whFilter != null && !whFilter.isEmpty()) {
            sql += "AND r.warehouse_id = ? ";
            inputs.add(whFilter);
        }
        if (createdByFilter != null) {
            sql += "AND r.created_by = ? ";
            inputs.add(createdByFilter);
        }
        if (search != null && !search.trim().isEmpty()) {
            sql += "AND (r.receipt_code LIKE ? OR so.order_code LIKE ? "
                    + "OR c.name LIKE ? OR u1.name LIKE ? OR liq.liquidation_code LIKE ? OR po.po_code LIKE ?) ";
            String like = "%" + search.trim() + "%";
            inputs.add(like);
            inputs.add(like);
            inputs.add(like);
            inputs.add(like);
            inputs.add(like);
            inputs.add(like);
        }
        if (relatedTypeFilter != null && !relatedTypeFilter.isEmpty()) {
            if ("ORDER".equals(relatedTypeFilter)) {
                sql += "AND r.order_id IS NOT NULL ";
            } else if ("LIQUIDATION".equals(relatedTypeFilter)) {
                sql += "AND r.liquidation_id IS NOT NULL ";
            }
        }
        sql += "ORDER BY r.created_at DESC";
        try {
            connection = getConnection();
            statement = connection.prepareStatement(sql);
            for (int i = 0; i < inputs.size(); i++) {
                statement.setObject(i + 1, inputs.get(i));
            }
            resultSet = statement.executeQuery();
            while (resultSet.next()) {
                allReceipts.add(getFromResultSet(resultSet));
            }
            if (allReceipts.isEmpty()) {
                return allReceipts;
            }
            int start = (page - 1) * pageSize;
            int end = Math.min(start + pageSize, allReceipts.size());
            if (start > allReceipts.size()) {
                return new ArrayList<>();
            }
            return allReceipts.subList(start, end);
        } catch (SQLException ex) {
            System.out.println(ex.getMessage());
        } finally {
            closeResources();
        }
        return new ArrayList<>();
    }

    public int countWithFilters(String typeFilter, String statusFilter, String whFilter, String search, Integer createdByFilter, String relatedTypeFilter, Integer currentUserId) {
        String sql = "SELECT COUNT(*) FROM receipt r "
                + "LEFT JOIN user u1 ON r.created_by = u1.id "
                + "LEFT JOIN sale_order so ON r.order_id = so.order_id "
                + "LEFT JOIN liquidation liq ON r.liquidation_id = liq.liquidation_id "
                + "LEFT JOIN customer c ON so.customer_id = c.id OR liq.customer_id = c.id "
                + "LEFT JOIN category cr ON r.reason_id = cr.id "
                + "WHERE 1=1 ";
        List<Object> inputs = new ArrayList<>();
        if (typeFilter != null && !typeFilter.isEmpty()) {
            sql += "AND r.receipt_type = ? ";
            inputs.add(typeFilter);
        }
        if (statusFilter != null && !statusFilter.isEmpty()) {
            sql += "AND r.status = ? ";
            inputs.add(statusFilter);
        }
        if (whFilter != null && !whFilter.isEmpty()) {
            sql += "AND r.warehouse_id = ? ";
            inputs.add(whFilter);
        }
        if (createdByFilter != null) {
            sql += "AND r.created_by = ? ";
            inputs.add(createdByFilter);
        }
        if (search != null && !search.trim().isEmpty()) {
            sql += "AND (r.receipt_code LIKE ? OR so.order_code LIKE ? "
                    + "OR c.name LIKE ? OR u1.name LIKE ? OR liq.liquidation_code LIKE ?) ";
            String like = "%" + search.trim() + "%";
            inputs.add(like);
            inputs.add(like);
            inputs.add(like);
            inputs.add(like);
            inputs.add(like);
        }
        if (relatedTypeFilter != null && !relatedTypeFilter.isEmpty()) {
            if ("ORDER".equals(relatedTypeFilter)) {
                sql += "AND r.order_id IS NOT NULL ";
            } else if ("LIQUIDATION".equals(relatedTypeFilter)) {
                sql += "AND r.liquidation_id IS NOT NULL ";
            }
        }
        try {
            connection = getConnection();
            statement = connection.prepareStatement(sql);
            for (int i = 0; i < inputs.size(); i++) {
                statement.setObject(i + 1, inputs.get(i));
            }
            resultSet = statement.executeQuery();
            if (resultSet.next()) {
                return resultSet.getInt(1);
            }
        } catch (SQLException ex) {
            System.out.println(ex.getMessage());
        } finally {
            closeResources();
        }
        return 0;
    }

    public Receipt findById(int receiptId) {
        String sql = "SELECT r.*, w.name AS warehouse_name, "
                + "u1.name AS created_by_name, u2.name AS approved_by_name, "
                + "so.order_code, liq.liquidation_code, liq.liquidation_id, c.name AS customer_name, cr.name AS reason_name, "
                + "po.po_code AS purchase_order_code, "
                + "tr.transfer_code AS transfer_code, "
                + "rexp.receipt_code AS related_export_receipt_code "
                + "FROM receipt r "
                + "LEFT JOIN warehouse w ON r.warehouse_id = w.warehouse_id "
                + "LEFT JOIN user u1 ON r.created_by = u1.id "
                + "LEFT JOIN user u2 ON r.approved_by = u2.id "
                + "LEFT JOIN sale_order so ON r.order_id = so.order_id "
                + "LEFT JOIN purchase_order po ON r.purchase_order_id = po.po_id "
                + "LEFT JOIN liquidation liq ON r.liquidation_id = liq.liquidation_id "
                + "LEFT JOIN customer c ON so.customer_id = c.id OR liq.customer_id = c.id "
                + "LEFT JOIN category cr ON r.reason_id = cr.id "
                + "LEFT JOIN transfer tr ON r.linked_transfer_id = tr.transfer_id "
                + "LEFT JOIN receipt rexp ON r.related_export_receipt_id = rexp.receipt_id "
                + "WHERE r.receipt_id = ?";
        try {
            connection = getConnection();
            statement = connection.prepareStatement(sql);
            statement.setInt(1, receiptId);
            resultSet = statement.executeQuery();
            if (resultSet.next()) {
                Receipt r = getFromResultSet(resultSet);
                ReceiptDetailDAO rdDAO = new ReceiptDetailDAO();
                r.setDetails(rdDAO.findByReceiptId(receiptId));
                return r;
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        } finally {
            closeResources();
        }
        return null;
    }

    public int insertImport(Receipt r, List<ReceiptDetail> details, int userId) throws SQLException {
        String status = r.getStatus();
        if (status == null || status.trim().isEmpty()) {
            status = GlobalUtils.RECEIPT_STATUS_PENDING;
        }
        String sql = "INSERT INTO receipt (receipt_code, receipt_type, order_id, purchase_order_id, liquidation_id, "
                + "linked_transfer_id, related_export_receipt_id, "
                + "warehouse_id, created_by, status, note, reason_id, created_at, approved_by, approved_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        Connection conn = null;
        try {
            conn = getConnection();
            conn.setAutoCommit(false);
            int receiptId;
            try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, r.getReceiptCode());
                ps.setString(2, r.getReceiptType());
                if (r.getOrderId() != null) {
                    ps.setInt(3, r.getOrderId());
                } else {
                    ps.setNull(3, Types.INTEGER);
                }
                if (r.getPurchaseOrderId() != null) {
                    ps.setInt(4, r.getPurchaseOrderId());
                } else {
                    ps.setNull(4, Types.INTEGER);
                }
                if (r.getLiquidationId() != null) {
                    ps.setInt(5, r.getLiquidationId());
                } else {
                    ps.setNull(5, Types.INTEGER);
                }
                if (r.getLinkedTransferId() != null) {
                    ps.setInt(6, r.getLinkedTransferId());
                } else {
                    ps.setNull(6, Types.INTEGER);
                }
                if (r.getRelatedExportReceiptId() != null) {
                    ps.setInt(7, r.getRelatedExportReceiptId());
                } else {
                    ps.setNull(7, Types.INTEGER);
                }
                ps.setInt(8, r.getWarehouseId());
                ps.setInt(9, r.getCreatedBy());
                ps.setString(10, status);
                ps.setString(11, r.getNote());
                if (r.getReasonId() != null) {
                    ps.setInt(12, r.getReasonId());
                } else {
                    ps.setNull(12, Types.INTEGER);
                }
                ps.setTimestamp(13, Timestamp.valueOf(LocalDateTime.now()));
                if (r.getApprovedBy() != null) {
                    ps.setInt(14, r.getApprovedBy());
                } else {
                    ps.setNull(14, Types.INTEGER);
                }
                if (r.getApprovedAt() != null) {
                    ps.setTimestamp(15, Timestamp.valueOf(r.getApprovedAt()));
                } else {
                    ps.setNull(15, Types.TIMESTAMP);
                }
                int affectedRows = ps.executeUpdate();
                if (affectedRows == 0) {
                    throw new SQLException("Không thể tạo phiếu");
                }
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (!rs.next()) {
                        throw new SQLException("Không thể tạo phiếu, không lấy được ID");
                    }
                    receiptId = rs.getInt(1);
                }
            }
            boolean isTransferImport = r.getRelatedExportReceiptId() != null && r.getRelatedExportReceiptId() > 0;
            InventoryDAO invDAO = new InventoryDAO();
            ReceiptDetailDAO detailDAO = new ReceiptDetailDAO();
            if (isTransferImport) {
                for (ReceiptDetail d : details) {
                    if (!invDAO.completeTransferImport(conn, d.getInventoryId(), r.getWarehouseId())) {
                        throw new SQLException("Số serial inventory_id=" + d.getInventoryId() + " không ở trạng thái IN_TRANSIT");
                    }
                    d.setReceiptId(receiptId);
                }
            } else {
                for (ReceiptDetail d : details) {
                    int existingInvId = d.getInventoryId();
                    if (existingInvId > 0) {
                        Inventory existingInv = invDAO.findBySerialNumber(d.getSerialNumber());
                        if (existingInv == null || existingInv.getInventoryId() != existingInvId
                                || !"SOLD".equals(existingInv.getStatus())) {
                            throw new SQLException("Số serial '" + d.getSerialNumber() + "' không còn ở trạng thái SOLD");
                        }
                        int updated = invDAO.reactivateSold(conn, existingInvId, d.getGeneratorId(), r.getWarehouseId());
                        if (updated <= 0) {
                            throw new SQLException("Không thể nhập lại số serial '" + d.getSerialNumber() + "'");
                        }
                        d.setInventoryId(existingInvId);
                    } else {
                        if (invDAO.serialExists(conn, d.getSerialNumber())) {
                            throw new SQLException("Số serial '" + d.getSerialNumber() + "' đã tồn tại");
                        }
                        int invId = invDAO.insertInStock(conn, d.getGeneratorId(), d.getSerialNumber(), r.getWarehouseId());
                        if (invId <= 0) {
                            throw new SQLException("Không thể tạo số serial '" + d.getSerialNumber() + "'");
                        }
                        d.setInventoryId(invId);
                    }
                    d.setReceiptId(receiptId);
                }
            }
            detailDAO.batchInsert(conn, details);
            writeStockCardsForImport(conn, receiptId, r.getReceiptCode(), r.getWarehouseId(), userId, details);
            if (r.getPurchaseOrderId() != null && r.getPurchaseOrderId() > 0) {
                int poId = r.getPurchaseOrderId();
                String updatePoSql = "UPDATE purchase_order SET status = 'COMPLETED' WHERE po_id = ?";
                try (PreparedStatement updatePoPs = conn.prepareStatement(updatePoSql)) {
                    updatePoPs.setInt(1, poId);
                    updatePoPs.executeUpdate();
                }
            }
            conn.commit();
            return receiptId;
        } catch (SQLException e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ex) {
                    /* ignore */ }
            }
            throw e;
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                } catch (SQLException e) {
                    /* ignore */ }
                try {
                    conn.close();
                } catch (SQLException e) {
                    /* ignore */ }
            }
        }
    }

    public int insertExport(Receipt r, List<ReceiptDetail> details, int userId) throws SQLException {
        String status = r.getStatus();
        if (status == null || status.trim().isEmpty()) {
            status = GlobalUtils.RECEIPT_STATUS_PENDING;
        }
        boolean isTransfer = r.getLinkedTransferId() != null && r.getLinkedTransferId() > 0;
        boolean isLiquidation = r.getLiquidationId() != null && r.getLiquidationId() > 0;
        String sql = "INSERT INTO receipt (receipt_code, receipt_type, order_id, purchase_order_id, liquidation_id, "
                + "linked_transfer_id, related_export_receipt_id, "
                + "warehouse_id, created_by, status, note, reason_id, created_at, approved_by, approved_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        Connection conn = null;
        try {
            conn = getConnection();
            conn.setAutoCommit(false);
            int receiptId;
            try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, r.getReceiptCode());
                ps.setString(2, r.getReceiptType());
                if (r.getOrderId() != null) {
                    ps.setInt(3, r.getOrderId());
                } else {
                    ps.setNull(3, Types.INTEGER);
                }
                if (r.getPurchaseOrderId() != null) {
                    ps.setInt(4, r.getPurchaseOrderId());
                } else {
                    ps.setNull(4, Types.INTEGER);
                }
                if (r.getLiquidationId() != null) {
                    ps.setInt(5, r.getLiquidationId());
                } else {
                    ps.setNull(5, Types.INTEGER);
                }
                if (r.getLinkedTransferId() != null) {
                    ps.setInt(6, r.getLinkedTransferId());
                } else {
                    ps.setNull(6, Types.INTEGER);
                }
                if (r.getRelatedExportReceiptId() != null) {
                    ps.setInt(7, r.getRelatedExportReceiptId());
                } else {
                    ps.setNull(7, Types.INTEGER);
                }
                ps.setInt(8, r.getWarehouseId());
                ps.setInt(9, r.getCreatedBy());
                ps.setString(10, status);
                ps.setString(11, r.getNote());
                if (r.getReasonId() != null) {
                    ps.setInt(12, r.getReasonId());
                } else {
                    ps.setNull(12, Types.INTEGER);
                }
                ps.setTimestamp(13, Timestamp.valueOf(LocalDateTime.now()));
                if (r.getApprovedBy() != null) {
                    ps.setInt(14, r.getApprovedBy());
                } else {
                    ps.setNull(14, Types.INTEGER);
                }
                if (r.getApprovedAt() != null) {
                    ps.setTimestamp(15, Timestamp.valueOf(r.getApprovedAt()));
                } else {
                    ps.setNull(15, Types.TIMESTAMP);
                }
                int affectedRows = ps.executeUpdate();
                if (affectedRows == 0) {
                    throw new SQLException("Không thể tạo phiếu");
                }
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (!rs.next()) {
                        throw new SQLException("Không thể tạo phiếu, không lấy được ID");
                    }
                    receiptId = rs.getInt(1);
                }
            }
            InventoryDAO invDAO = new InventoryDAO();
            ReceiptDetailDAO detailDAO = new ReceiptDetailDAO();
            for (ReceiptDetail d : details) {
                if (d.getSerialNumber() == null || d.getSerialNumber().trim().isEmpty()) {
                    continue;
                }
                String sn = d.getSerialNumber().trim();
                Inventory inv = invDAO.findBySerialNumber(sn);
                if (inv == null) {
                    throw new SQLException("Số serial \"" + sn + "\" không tồn tại trong hệ thống");
                }
                if (isLiquidation) {
                    if (!invDAO.isPendingLiquidationAtWarehouse(conn, sn, r.getWarehouseId())) {
                        throw new SQLException("Số serial \"" + sn + "\" không ở trạng thái ĐANG THANH LÝ tại kho này");
                    }
                    if (invDAO.markAsExportedFromPendingLiquidation(conn, inv.getInventoryId()) <= 0) {
                        throw new SQLException("Số serial \"" + sn + "\" không ở trạng thái PENDING_LIQUIDATION");
                    }
                } else {
                    if (!invDAO.isInStockAtWarehouse(sn, r.getWarehouseId())) {
                        throw new SQLException("Số serial \"" + sn + "\" không ở trạng thái IN_STOCK tại kho này");
                    }
                    boolean marked = isTransfer
                            ? invDAO.markAsInTransit(conn, inv.getInventoryId())
                            : invDAO.markAsExported(conn, inv.getInventoryId(), GlobalUtils.INVENTORY_STATUS_SOLD);
                    if (!marked) {
                        throw new SQLException("Số serial \"" + sn + "\" không ở trạng thái IN_STOCK");
                    }
                }
                d.setReceiptId(receiptId);
                d.setInventoryId(inv.getInventoryId());
            }
            detailDAO.batchInsert(conn, details);
            if (isTransfer) {
                writeStockCardsForTransfer(conn, receiptId, r.getReceiptCode(), r.getWarehouseId(), userId, details);
            } else {
                writeStockCardsForExport(conn, receiptId, r.getReceiptCode(), r.getWarehouseId(), userId, details);
            }
            if (r.getOrderId() != null && r.getOrderId() > 0) {
                String updateSoSql = "UPDATE sale_order SET status = 'COMPLETED' WHERE order_id = ?";
                try (PreparedStatement updateSoPs = conn.prepareStatement(updateSoSql)) {
                    updateSoPs.setInt(1, r.getOrderId());
                    updateSoPs.executeUpdate();
                }
            }
            conn.commit();
            return receiptId;
        } catch (SQLException e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ex) {
                    /* ignore */ }
            }
            throw e;
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                } catch (SQLException e) {
                    /* ignore */ }
                try {
                    conn.close();
                } catch (SQLException e) {
                    /* ignore */ }
            }
        }
    }

    public void writeStockCardsForTransfer(Connection conn, int receiptId, String receiptCode,
            int warehouseId, int createdBy,
            List<ReceiptDetail> details) throws SQLException {
        if (details == null || details.isEmpty()) {
            return;
        }
        StockCardDAO scDAO = new StockCardDAO();
        Map<Integer, List<ReceiptDetail>> grouped = new LinkedHashMap<>();
        for (ReceiptDetail d : details) {
            if (d.getGeneratorId() > 0) {
                grouped.computeIfAbsent(d.getGeneratorId(), k -> new ArrayList<>()).add(d);
            }
        }
        String latestSql = "SELECT quantity_after FROM stock_card WHERE warehouse_id = ? AND generator_id = ? ORDER BY created_at DESC, stock_card_id DESC LIMIT 1";
        for (Map.Entry<Integer, List<ReceiptDetail>> entry : grouped.entrySet()) {
            int genId = entry.getKey();
            int totalQty = entry.getValue().size();
            int prevQty = 0;
            try (PreparedStatement qtyPs = conn.prepareStatement(latestSql)) {
                qtyPs.setInt(1, warehouseId);
                qtyPs.setInt(2, genId);
                try (ResultSet qtyRs = qtyPs.executeQuery()) {
                    if (qtyRs.next()) {
                        prevQty = qtyRs.getInt(1);
                    }
                }
            }
            int qtyAfter = Math.max(0, prevQty - totalQty);
            StockCard sc = new StockCard();
            sc.setWarehouseId(warehouseId);
            sc.setGeneratorId(genId);
            sc.setReceiptId(receiptId);
            sc.setTransactionType("EXPORT");
            sc.setQuantityChange(-totalQty);
            sc.setQuantityAfter(qtyAfter);
            sc.setReferenceNote("Phiếu xuất " + receiptCode + " (luân chuyển)");
            sc.setCreatedAt(LocalDateTime.now());
            sc.setCreatedBy(createdBy);
            scDAO.insert(conn, sc);
        }
    }

    // ===================== BÁO CÁO NHẬP / XUẤT =====================
    private String buildReportWhere(java.time.LocalDate from, java.time.LocalDate to,
            Integer warehouseId, List<Object> params, String receiptType) {
        StringBuilder w = new StringBuilder(" WHERE r.receipt_type = ?");
        params.add(receiptType);
        // He thong khong co workflow duyet that su, dung created_at lam moc thoi gian
        if (from != null) {
            w.append(" AND DATE(r.created_at) >= ?");
            params.add(Date.valueOf(from));
        }
        if (to != null) {
            w.append(" AND DATE(r.created_at) <= ?");
            params.add(Date.valueOf(to));
        }
        if (warehouseId != null) {
            w.append(" AND r.warehouse_id = ?");
            params.add(warehouseId);
        }
        return w.toString();
    }

    private void bindParams(PreparedStatement ps, List<Object> params) throws SQLException {
        for (int i = 0; i < params.size(); i++) {
            ps.setObject(i + 1, params.get(i));
        }
    }

    // Tổng quan: tổng phiếu, tổng máy, số phiếu theo trạng thái (chỉ tính COMPLETED/PENDING/CANCELLED).
    public Map<String, Object> getReportSummary(java.time.LocalDate from, java.time.LocalDate to,
            Integer warehouseId, String receiptType) {
        Map<String, Object> m = new java.util.HashMap<>();
        List<Object> params = new ArrayList<>();
        String where = buildReportWhere(from, to, warehouseId, params, receiptType);
        // Subquery loc ra danh sach receipt_id truoc de tranh double-count khi JOIN detail
        String sql = "SELECT COUNT(*) AS total_receipts, "
                + "COALESCE(SUM(machine_count), 0) AS total_machines, "
                + "SUM(CASE WHEN status = 'COMPLETED' THEN 1 ELSE 0 END) AS completed_count, "
                + "SUM(CASE WHEN status = 'PENDING' THEN 1 ELSE 0 END) AS pending_count, "
                + "SUM(CASE WHEN status = 'CANCELLED' THEN 1 ELSE 0 END) AS cancelled_count "
                + "FROM (SELECT r.receipt_id, r.status, COUNT(rd.receipt_detail_id) AS machine_count "
                + "      FROM receipt r LEFT JOIN receipt_detail rd ON rd.receipt_id = r.receipt_id"
                + where
                + "      GROUP BY r.receipt_id, r.status) t";
        try {
            connection = getConnection();
            statement = connection.prepareStatement(sql);
            bindParams(statement, params);
            resultSet = statement.executeQuery();
            if (resultSet.next()) {
                int totalReceipts = resultSet.getInt("total_receipts");
                int totalMachines = resultSet.getInt("total_machines");
                int completedCount = resultSet.getInt("completed_count");
                int pendingCount = resultSet.getInt("pending_count");
                int cancelledCount = resultSet.getInt("cancelled_count");
                double completionRate = totalReceipts > 0
                        ? Math.round((completedCount * 1000.0) / totalReceipts) / 10.0
                        : 0.0;
                m.put("totalReceipts", totalReceipts);
                m.put("totalMachines", totalMachines);
                m.put("completedCount", completedCount);
                m.put("pendingCount", pendingCount);
                m.put("cancelledCount", cancelledCount);
                m.put("completionRate", completionRate);
            } else {
                m.put("totalReceipts", 0);
                m.put("totalMachines", 0);
                m.put("completedCount", 0);
                m.put("pendingCount", 0);
                m.put("cancelledCount", 0);
                m.put("completionRate", 0.0);
            }
        } catch (Exception e) {
            System.out.println("Error getReportSummary: " + e.getMessage());
        } finally {
            closeResources();
        }
        return m;
    }

    // Phân tích theo kho: kho | số phiếu | số máy.
    public List<Map<String, Object>> getReportByWarehouse(java.time.LocalDate from, java.time.LocalDate to,
            Integer warehouseId, String receiptType) {
        List<Map<String, Object>> list = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        String where = buildReportWhere(from, to, warehouseId, params, receiptType);
        // Subquery truoc de tranh double-count tu LEFT JOIN receipt_detail
        String sql = "SELECT warehouse_name, COUNT(*) AS receipt_count, SUM(machine_count) AS machine_count "
                + "FROM (SELECT r.warehouse_id, w.name AS warehouse_name, r.receipt_id, "
                + "             COUNT(rd.receipt_detail_id) AS machine_count "
                + "      FROM receipt r "
                + "      JOIN warehouse w ON r.warehouse_id = w.warehouse_id "
                + "      LEFT JOIN receipt_detail rd ON rd.receipt_id = r.receipt_id"
                + where
                + "      GROUP BY r.warehouse_id, w.name, r.receipt_id) t "
                + "GROUP BY warehouse_id, warehouse_name ORDER BY receipt_count DESC";
        try {
            connection = getConnection();
            statement = connection.prepareStatement(sql);
            bindParams(statement, params);
            resultSet = statement.executeQuery();
            while (resultSet.next()) {
                Map<String, Object> r = new java.util.HashMap<>();
                r.put("warehouseName", resultSet.getString("warehouse_name"));
                r.put("receiptCount", resultSet.getInt("receipt_count"));
                r.put("machineCount", resultSet.getInt("machine_count"));
                list.add(r);
            }
        } catch (Exception e) {
            System.out.println("Error getReportByWarehouse: " + e.getMessage());
        } finally {
            closeResources();
        }
        return list;
    }

    // Phân tích theo trạng thái: trạng thái | số phiếu.
    public List<Map<String, Object>> getReportByStatus(java.time.LocalDate from, java.time.LocalDate to,
            Integer warehouseId, String receiptType) {
        List<Map<String, Object>> list = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        String where = buildReportWhere(from, to, warehouseId, params, receiptType);
        String sql = "SELECT r.status, COUNT(r.receipt_id) AS receipt_count "
                + "FROM receipt r"
                + where
                + " GROUP BY r.status ORDER BY receipt_count DESC";
        try {
            connection = getConnection();
            statement = connection.prepareStatement(sql);
            bindParams(statement, params);
            resultSet = statement.executeQuery();
            while (resultSet.next()) {
                Map<String, Object> r = new java.util.HashMap<>();
                r.put("status", resultSet.getString("status"));
                r.put("receiptCount", resultSet.getInt("receipt_count"));
                list.add(r);
            }
        } catch (Exception e) {
            System.out.println("Error getReportByStatus: " + e.getMessage());
        } finally {
            closeResources();
        }
        return list;
    }

    // Xu hướng theo tháng: tháng (yyyy-MM) | số phiếu | số máy.
    public List<Map<String, Object>> getReportMonthlyTrend(java.time.LocalDate from, java.time.LocalDate to,
            Integer warehouseId, String receiptType) {
        List<Map<String, Object>> list = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        String where = buildReportWhere(from, to, warehouseId, params, receiptType);
        String sql = "SELECT ym, COUNT(*) AS receipt_count, SUM(machine_count) AS machine_count "
                + "FROM (SELECT r.receipt_id, "
                + "             DATE_FORMAT(r.created_at, '%Y-%m') AS ym, "
                + "             COUNT(rd.receipt_detail_id) AS machine_count "
                + "      FROM receipt r LEFT JOIN receipt_detail rd ON rd.receipt_id = r.receipt_id"
                + where
                + "      GROUP BY r.receipt_id) t "
                + "GROUP BY ym ORDER BY ym ASC";
        try {
            connection = getConnection();
            statement = connection.prepareStatement(sql);
            bindParams(statement, params);
            resultSet = statement.executeQuery();
            while (resultSet.next()) {
                Map<String, Object> r = new java.util.HashMap<>();
                r.put("month", resultSet.getString("ym"));
                r.put("receiptCount", resultSet.getInt("receipt_count"));
                r.put("machineCount", resultSet.getInt("machine_count"));
                list.add(r);
            }
        } catch (Exception e) {
            System.out.println("Error getReportMonthlyTrend: " + e.getMessage());
        } finally {
            closeResources();
        }
        return list;
    }

    // Chi tiết từng phiếu (gom GROUP BY receipt_id, mỗi dòng = 1 phiếu).
    public List<Map<String, Object>> getReportDetailList(java.time.LocalDate from, java.time.LocalDate to,
            Integer warehouseId, String receiptType,
            int limit, int offset) {
        List<Map<String, Object>> list = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        String where = buildReportWhere(from, to, warehouseId, params, receiptType);
        // Pre-aggregate machine_count trong subquery de tranh GROUP BY o ngoai (MySQL ONLY_FULL_GROUP_BY)
        String sql = "SELECT r.receipt_id, r.receipt_code, r.status, r.note, r.created_at, "
                + "w.name AS warehouse_name, "
                + "COALESCE(mc.machine_count, 0) AS machine_count, "
                + "cu.name AS creator_name, "
                + "po.po_code AS purchase_order_code, so.order_code, "
                + "liq.liquidation_code "
                + "FROM receipt r "
                + "JOIN warehouse w ON r.warehouse_id = w.warehouse_id "
                + "LEFT JOIN (SELECT receipt_id, COUNT(*) AS machine_count "
                + "             FROM receipt_detail GROUP BY receipt_id) mc ON mc.receipt_id = r.receipt_id "
                + "LEFT JOIN user cu ON r.created_by = cu.id "
                + "LEFT JOIN purchase_order po ON r.purchase_order_id = po.po_id "
                + "LEFT JOIN sale_order so ON r.order_id = so.order_id "
                + "LEFT JOIN liquidation liq ON liq.converted_receipt_id = r.receipt_id"
                + where
                + " ORDER BY r.created_at DESC, r.receipt_id DESC "
                + "LIMIT ? OFFSET ?";
        params.add(limit);
        params.add(offset);
        try {
            connection = getConnection();
            statement = connection.prepareStatement(sql);
            bindParams(statement, params);
            resultSet = statement.executeQuery();
            DateTimeFormatter df = DateTimeFormatter.ofPattern("dd/MM/yyyy");
            while (resultSet.next()) {
                Map<String, Object> r = new java.util.HashMap<>();
                r.put("receiptId", resultSet.getInt("receipt_id"));
                r.put("receiptCode", resultSet.getString("receipt_code"));
                r.put("status", resultSet.getString("status"));
                r.put("warehouseName", resultSet.getString("warehouse_name"));
                r.put("machineCount", resultSet.getInt("machine_count"));
                r.put("creatorName", resultSet.getString("creator_name"));
                r.put("purchaseOrderCode", resultSet.getString("purchase_order_code"));
                r.put("orderCode", resultSet.getString("order_code"));
                r.put("liquidationCode", resultSet.getString("liquidation_code"));
                r.put("note", resultSet.getString("note"));
                // He thong khong co workflow duyet that su -> hien thi theo ngay tao
                LocalDateTime createdAt = resultSet.getObject("created_at", LocalDateTime.class);
                r.put("createdAtStr", createdAt != null ? createdAt.toLocalDate().format(df) : "");
                list.add(r);
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Error getReportDetailList: " + e.getMessage());
        } finally {
            closeResources();
        }
        return list;
    }

    public int countReportDetailList(java.time.LocalDate from, java.time.LocalDate to,
            Integer warehouseId, String receiptType) {
        List<Object> params = new ArrayList<>();
        String where = buildReportWhere(from, to, warehouseId, params, receiptType);
        String sql = "SELECT COUNT(*) FROM (SELECT r.receipt_id "
                + "FROM receipt r"
                + where
                + " GROUP BY r.receipt_id) t";
        try {
            connection = getConnection();
            statement = connection.prepareStatement(sql);
            bindParams(statement, params);
            resultSet = statement.executeQuery();
            if (resultSet.next()) {
                return resultSet.getInt(1);
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Error countReportDetailList: " + e.getMessage());
        } finally {
            closeResources();
        }
        return 0;
    }

    // ===================== END BÁO CÁO NHẬP / XUẤT =====================
    public void writeStockCardsForImport(Connection conn, int receiptId, String receiptCode,
            int warehouseId, int createdBy,
            List<ReceiptDetail> details) throws SQLException {
        if (details == null || details.isEmpty()) {
            return;
        }
        StockCardDAO scDAO = new StockCardDAO();
        Map<Integer, List<ReceiptDetail>> grouped = new LinkedHashMap<>();
        for (ReceiptDetail d : details) {
            if (d.getGeneratorId() > 0) {
                grouped.computeIfAbsent(d.getGeneratorId(), k -> new ArrayList<>()).add(d);
            }
        }
        String latestSql = "SELECT quantity_after FROM stock_card WHERE warehouse_id = ? AND generator_id = ? ORDER BY created_at DESC, stock_card_id DESC LIMIT 1";
        for (Map.Entry<Integer, List<ReceiptDetail>> entry : grouped.entrySet()) {
            int genId = entry.getKey();
            int totalQty = entry.getValue().size();
            int prevQty = 0;
            try (PreparedStatement qtyPs = conn.prepareStatement(latestSql)) {
                qtyPs.setInt(1, warehouseId);
                qtyPs.setInt(2, genId);
                try (ResultSet qtyRs = qtyPs.executeQuery()) {
                    if (qtyRs.next()) {
                        prevQty = qtyRs.getInt(1);
                    }
                }
            }
            int qtyAfter = prevQty + totalQty;
            StockCard sc = new StockCard();
            sc.setWarehouseId(warehouseId);
            sc.setGeneratorId(genId);
            sc.setReceiptId(receiptId);
            sc.setTransactionType("IMPORT");
            sc.setQuantityChange(totalQty);
            sc.setQuantityAfter(qtyAfter);
            sc.setReferenceNote("Phiếu " + receiptCode);
            sc.setCreatedAt(LocalDateTime.now());
            sc.setCreatedBy(createdBy);
            scDAO.insert(conn, sc);
        }
    }

    public void writeStockCardsForExport(Connection conn, int receiptId, String receiptCode,
            int warehouseId, int createdBy,
            List<ReceiptDetail> details) throws SQLException {
        if (details == null || details.isEmpty()) {
            return;
        }
        StockCardDAO scDAO = new StockCardDAO();
        Map<Integer, List<ReceiptDetail>> grouped = new LinkedHashMap<>();
        for (ReceiptDetail d : details) {
            if (d.getGeneratorId() > 0) {
                grouped.computeIfAbsent(d.getGeneratorId(), k -> new ArrayList<>()).add(d);
            }
        }
        String latestSql = "SELECT quantity_after FROM stock_card WHERE warehouse_id = ? AND generator_id = ? ORDER BY created_at DESC, stock_card_id DESC LIMIT 1";
        for (Map.Entry<Integer, List<ReceiptDetail>> entry : grouped.entrySet()) {
            int genId = entry.getKey();
            int totalQty = entry.getValue().size();
            int prevQty = 0;
            try (PreparedStatement qtyPs = conn.prepareStatement(latestSql)) {
                qtyPs.setInt(1, warehouseId);
                qtyPs.setInt(2, genId);
                try (ResultSet qtyRs = qtyPs.executeQuery()) {
                    if (qtyRs.next()) {
                        prevQty = qtyRs.getInt(1);
                    }
                }
            }
            int qtyAfter = Math.max(0, prevQty - totalQty);
            StockCard sc = new StockCard();
            sc.setWarehouseId(warehouseId);
            sc.setGeneratorId(genId);
            sc.setReceiptId(receiptId);
            sc.setTransactionType("EXPORT");
            sc.setQuantityChange(-totalQty);
            sc.setQuantityAfter(qtyAfter);
            sc.setReferenceNote("Phiếu " + receiptCode);
            sc.setCreatedAt(LocalDateTime.now());
            sc.setCreatedBy(createdBy);
            scDAO.insert(conn, sc);
        }
    }

    public boolean requestRevision(int receiptId, int managerId, Integer reasonId, String reasonNote) {
        String sql = "UPDATE receipt SET status = 'CANCELLED', approved_by = ?, "
                + "reason_id = ?, reason_note = ? "
                + "WHERE receipt_id = ? AND status = '" + GlobalUtils.RECEIPT_STATUS_PENDING + "'";
        try {
            connection = getConnection();
            statement = connection.prepareStatement(sql);
            statement.setInt(1, managerId);
            if (reasonId != null) {
                statement.setInt(2, reasonId);
            } else {
                statement.setNull(2, Types.INTEGER);
            }
            if (reasonNote != null && !reasonNote.trim().isEmpty()) {
                statement.setString(3, reasonNote.trim());
            } else {
                statement.setNull(3, Types.VARCHAR);
            }
            statement.setInt(4, receiptId);
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        } finally {
            closeResources();
        }
        return false;
    }

    public boolean updateReceipt(Receipt r, List<ReceiptDetail> newDetails, int userId) {
        return updateReceiptInternalMulti(r, newDetails, userId,
                GlobalUtils.RECEIPT_STATUS_PENDING, new String[]{GlobalUtils.RECEIPT_STATUS_PENDING});
    }

    private boolean updateReceiptInternalMulti(Receipt r, List<ReceiptDetail> newDetails, int userId,
            String newStatus, String[] allowedCurrentStatuses) {
        StringBuilder placeholders = new StringBuilder();
        for (int i = 0; i < allowedCurrentStatuses.length; i++) {
            if (i > 0) {
                placeholders.append(",");
            }
            placeholders.append("?");
        }
        String updateSql = "UPDATE receipt SET warehouse_id = ?, note = ?, "
                + "status = ?, approved_by = NULL, reason_id = ? "
                + "WHERE receipt_id = ? AND status IN (" + placeholders + ") AND created_by = ?";
        String deleteDetailSql = "DELETE FROM receipt_detail WHERE receipt_id = ?";
        String insertDetailSql = "INSERT INTO receipt_detail "
                + "(receipt_id, inventory_id, note) VALUES (?, ?, ?)";
        Connection conn = null;
        try {
            conn = getConnection();
            conn.setAutoCommit(false);

            // Lay receipt_type va warehouse_id de biet cleanup inventory the nao
            String typeSql = "SELECT receipt_type, warehouse_id FROM receipt WHERE receipt_id = ?";
            String receiptType = "";
            int warehouseId = 0;
            try (PreparedStatement ps = conn.prepareStatement(typeSql)) {
                ps.setInt(1, r.getReceiptId());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        receiptType = rs.getString("receipt_type");
                        warehouseId = rs.getInt("warehouse_id");
                    }
                }
            }

            // Lay danh sach inventory_id cu (truoc khi xoa receipt_detail)
            List<Integer> oldInventoryIds = new ArrayList<>();
            String selInvSql = "SELECT inventory_id FROM receipt_detail WHERE receipt_id = ?";
            try (PreparedStatement ps = conn.prepareStatement(selInvSql)) {
                ps.setInt(1, r.getReceiptId());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        oldInventoryIds.add(rs.getInt("inventory_id"));
                    }
                }
            }

            // Cap nhat receipt header
            try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                ps.setInt(1, r.getWarehouseId());
                ps.setString(2, r.getNote());
                ps.setString(3, newStatus);
                if (r.getReasonId() != null) {
                    ps.setInt(4, r.getReasonId());
                } else {
                    ps.setNull(4, Types.INTEGER);
                }
                ps.setInt(5, r.getReceiptId());
                int statusIdx = 6;
                for (String s : allowedCurrentStatuses) {
                    ps.setString(statusIdx++, s);
                }
                ps.setInt(statusIdx, userId);
                int affected = ps.executeUpdate();
                if (affected == 0) {
                    conn.rollback();
                    return false;
                }
            }

            // Xoa receipt_detail cu truoc (tranh FK conflict khi xoa inventory sau)
            try (PreparedStatement ps = conn.prepareStatement(deleteDetailSql)) {
                ps.setInt(1, r.getReceiptId());
                ps.executeUpdate();
            }

            // Insert receipt_detail moi (inventory_id phai duoc set boi controller truoc do)
            try (PreparedStatement ps = conn.prepareStatement(insertDetailSql)) {
                for (ReceiptDetail d : newDetails) {
                    ps.setInt(1, r.getReceiptId());
                    ps.setInt(2, d.getInventoryId());
                    ps.setString(3, d.getNote());
                    ps.addBatch();
                }
                ps.executeBatch();
            }

            conn.commit();
            return true;
        } catch (SQLException e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ex) {
                    ex.printStackTrace();
                }
            }
            e.printStackTrace();
            return false;
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                    conn.close();
                } catch (SQLException ignored) {
                }
            }
        }
    }

    @Override
    public List<Receipt> findAll() {
        throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
    }

    @Override
    public boolean update(Receipt t) {
        throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
    }

    @Override
    public boolean delete(Receipt t) {
        throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
    }

    @Override
    public Receipt getFromResultSet(ResultSet rs) throws SQLException {
        Receipt r = new Receipt();
        r.setReceiptId(rs.getInt("receipt_id"));
        r.setReceiptCode(rs.getString("receipt_code"));
        r.setReceiptType(rs.getString("receipt_type"));
        int oid = rs.getInt("order_id");
        if (rs.wasNull()) {
            r.setOrderId(null);
        } else {
            r.setOrderId(oid);
        }
        int poid = rs.getInt("purchase_order_id");
        if (rs.wasNull()) {
            r.setPurchaseOrderId(null);
        } else {
            r.setPurchaseOrderId(poid);
        }
        try {
            int lt = rs.getInt("linked_transfer_id");
            if (!rs.wasNull()) {
                r.setLinkedTransferId(lt);
            }
        } catch (SQLException ignored) {
        }
        try {
            int re = rs.getInt("related_export_receipt_id");
            if (!rs.wasNull()) {
                r.setRelatedExportReceiptId(re);
            }
        } catch (SQLException ignored) {
        }
        r.setWarehouseId(rs.getInt("warehouse_id"));
        r.setCreatedBy(rs.getInt("created_by"));
        int aid = rs.getInt("approved_by");
        if (rs.wasNull()) {
            r.setApprovedBy(null);
        } else {
            r.setApprovedBy(aid);
        }
        r.setStatus(rs.getString("status"));
        r.setNote(rs.getString("note"));
        Timestamp aa = rs.getTimestamp("approved_at");
        if (aa != null) {
            r.setApprovedAt(aa.toLocalDateTime());
        }
        Timestamp ca = rs.getTimestamp("created_at");
        if (ca != null) {
            r.setCreatedAt(ca.toLocalDateTime());
        }
        Timestamp ua = rs.getTimestamp("updated_at");
        if (ua != null) {
            r.setUpdatedAt(ua.toLocalDateTime());
        }
        try {
            r.setWarehouseName(rs.getString("warehouse_name"));
        } catch (SQLException ignored) {
        }
        try {
            r.setCreatedByName(rs.getString("created_by_name"));
        } catch (SQLException ignored) {
        }
        try {
            r.setApprovedByName(rs.getString("approved_by_name"));
        } catch (SQLException ignored) {
        }
        try {
            r.setOrderCode(rs.getString("order_code"));
        } catch (SQLException ignored) {
        }
        try {
            r.setPurchaseOrderCode(rs.getString("purchase_order_code"));
        } catch (SQLException ignored) {
        }
        try {
            r.setLiquidationCode(rs.getString("liquidation_code"));
            int lid = rs.getInt("liquidation_id");
            if (!rs.wasNull()) {
                r.setLiquidationId(lid);
            }
        } catch (SQLException ignored) {
        }
        try {
            r.setCustomerName(rs.getString("customer_name"));
        } catch (SQLException ignored) {
        }
        try {
            r.setProposalCode(rs.getString("proposal_code"));
        } catch (SQLException ignored) {
        }
        int rid = rs.getInt("reason_id");
        if (!rs.wasNull()) {
            r.setReasonId(rid);
        }
        try {
            r.setReasonNote(rs.getString("reason_note"));
        } catch (SQLException ignored) {
        }
        try {
            r.setReasonName(rs.getString("reason_name"));
        } catch (SQLException ignored) {
        }
        try {
            r.setTransferCode(rs.getString("transfer_code"));
        } catch (SQLException ignored) {
        }
        try {
            r.setRelatedExportReceiptCode(rs.getString("related_export_receipt_code"));
        } catch (SQLException ignored) {
        }
        return r;
    }
//    public int insert(Connection conn, Receipt r) throws SQLException {
//        String status = r.getStatus();
//        if (status == null || status.trim().isEmpty()) {
//            status = GlobalUtils.RECEIPT_STATUS_PENDING;
//        }
//        String sql = "INSERT INTO receipt (receipt_code, receipt_type, order_id, proposal_id, "
//                + "warehouse_id, created_by, status, note, reason_id, created_at) "
//                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
//        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
//            ps.setString(1, r.getReceiptCode());
//            ps.setString(2, r.getReceiptType());
//            if (r.getOrderId() != null) {
//                ps.setInt(3, r.getOrderId());
//            } else {
//                ps.setNull(3, Types.INTEGER);
//            }
//            if (r.getProposalId() != null) {
//                ps.setInt(4, r.getProposalId());
//            } else {
//                ps.setNull(4, Types.INTEGER);
//            }
//            ps.setInt(5, r.getWarehouseId());
//            ps.setInt(6, r.getCreatedBy());
//            ps.setString(7, status);
//            ps.setString(8, r.getNote());
//            if (r.getReasonId() != null) {
//                ps.setInt(9, r.getReasonId());
//            } else {
//                ps.setNull(9, Types.INTEGER);
//            }
//            ps.setTimestamp(10, Timestamp.valueOf(LocalDateTime.now()));
//            int affectedRows = ps.executeUpdate();
//            if (affectedRows > 0) {
//                try (ResultSet rs = ps.getGeneratedKeys()) {
//                    if (rs.next()) {
//                        return rs.getInt(1);
//                    }
//                }
//            }
//        }
//        return -1;
//    }

    @Override
    public int insert(Receipt t) {
        throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
    }

}
