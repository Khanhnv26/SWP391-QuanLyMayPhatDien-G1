<%@ page contentType="text/html" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%
    java.time.format.DateTimeFormatter __rptFmt = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy");
    request.setAttribute("rptFmt", __rptFmt);
%>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Báo cáo - Warehouse OS</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/variables.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/base.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/sidebar.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/admin-user.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/inventory.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/report.css">
    <script src="https://cdn.jsdelivr.net/npm/chart.js@3.9.1/dist/chart.min.js"></script>
    <script src="${pageContext.request.contextPath}/assets/js/report.js"></script>
</head>
<body>
<div class="app">
    <c:set var="activePage" value="report-${reportType}" scope="request"/>
    <c:set var="sidebarPermissions" value="${sessionScope.userPermissions}" scope="request"/>
    <jsp:include page="/view/common/admin/aside.jsp"/>

    <main class="main-content">
        <div class="page-head">
            <h2>
                <c:choose>
                    <c:when test="${reportType == 'inventory'}">Báo cáo xuất nhập tồn</c:when>
                    <c:when test="${reportType == 'import'}">Báo cáo nhập kho</c:when>
                    <c:when test="${reportType == 'export'}">Báo cáo xuất kho</c:when>
                    <c:when test="${reportType == 'purchase'}">Báo cáo mua hàng</c:when>
                    <c:when test="${reportType == 'sales'}">Báo cáo bán hàng</c:when>
                </c:choose>
            </h2>
        </div>

        <div class="report-filter-bar">
            <form method="GET" action="${pageContext.request.contextPath}/reports" class="filter-form">
                <input type="hidden" name="type" value="${reportType}"/>
                <input type="hidden" name="page" value="1"/>

                <div class="search-input">
                    <svg viewBox="0 0 24 24"><circle cx="11" cy="11" r="8"/><path d="M21 21l-4.35-4.35"/></svg>
                    <input name="search" value="<c:out value="${search}"/>" placeholder="Mã phiếu, tên máy, serial..." autocomplete="off" />
                </div>

                <c:if test="${reportType != 'sales'}">
                    <select name="warehouseId" class="filter-select" onchange="this.form.submit()">
                        <option value="">Kho: Tất cả</option>
                        <c:forEach var="wh" items="${warehouses}">
                            <option value="${wh.warehouseId}" ${wh.warehouseId == selWarehouseId ? 'selected' : ''}>
                                <c:out value="${wh.name}"/>
                            </option>
                        </c:forEach>
                    </select>
                </c:if>

                <select name="month" class="filter-select filter-select-auto">
                    <c:forEach var="m" begin="1" end="12">
                        <option value="${m}" ${m == month ? 'selected' : ''}>Tháng ${m}</option>
                    </c:forEach>
                </select>
                <input type="number" name="year" value="${year}" class="year-input">

                <div class="spacer"></div>
                <button type="submit" class="btn btn-primary">
                    <svg class="icon" viewBox="0 0 24 24"><circle cx="11" cy="11" r="8"/><path d="M21 21l-4.35-4.35"/></svg>
                    Xem
                </button>
                <c:if test="${sessionScope.userPermissions.contains('reports.export')}">
                    <button type="button" class="btn btn-success" onclick="RPT.exportExcel()">
                        <svg class="icon" viewBox="0 0 24 24"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4M7 10l5 5 5-5M12 15V3"/></svg>
                        Xuất Excel
                    </button>
                </c:if>
            </form>
        </div>

        <div class="report-table-wrap">
            <c:choose>
                <%-- TỒN KHO --%>
                <c:when test="${reportType == 'inventory'}">
                    <div class="summary-cards">
                        <div class="summary-card">
                            <div class="summary-label">Tổng mẫu máy</div>
                            <div class="summary-value">${summary.totalModels}</div>
                        </div>
                        <div class="summary-card">
                            <div class="summary-label">Tồn đầu kì</div>
                            <div class="summary-value"><fmt:formatNumber value="${summary.totalOpen}" pattern="#,##0"/></div>
                        </div>
                        <div class="summary-card">
                            <div class="summary-label">Nhập trong kì</div>
                            <div class="summary-value val-accent"><fmt:formatNumber value="${summary.totalImport}" pattern="#,##0"/></div>
                        </div>
                        <div class="summary-card">
                            <div class="summary-label">Xuất trong kì</div>
                            <div class="summary-value val-info"><fmt:formatNumber value="${summary.totalExport}" pattern="#,##0"/></div>
                        </div>
                        <div class="summary-card">
                            <div class="summary-label">Tồn cuối kì</div>
                            <div class="summary-value"><fmt:formatNumber value="${summary.totalSerials}" pattern="#,##0"/></div>
                        </div>
                    </div>
                    <c:if test="${not empty trendJson}">
                    <div class="rpt-chart"><canvas id="chart0" height="80"></canvas></div>
                    <script>RPT.charts=RPT.charts||[];RPT.charts.push({id:'chart0',data:${trendJson},report:'inventory'});</script>
                    </c:if>
                    <table class="report-table">
                        <thead>
                            <tr>
                                <th>STT</th>
                                <th>Kho</th>
                                <th>Mẫu máy</th>
                                <th>Thương hiệu</th>
                                <th>Tồn đầu kì</th>
                                <th>Nhập</th>
                                <th>Xuất</th>
                                <th>Tồn cuối kì</th>
                            </tr>
                        </thead>
                        <tbody>
                            <c:forEach var="item" items="${inventoryItems}" varStatus="st">
                                <tr>
                                    <td>${st.index + 1 + (currentPage - 1) * 15}</td>
                                    <td><c:out value="${item.warehouseName}"/></td>
                                    <td><c:out value="${item.model}"/></td>
                                    <td><c:out value="${item.brand}"/></td>
                                    <td class="num">${item.openQuantity}</td>
                                    <td class="num val-accent">${item.importQuantity}</td>
                                    <td class="num val-info">${item.exportQuantity}</td>
                                    <td class="num">${item.closeQuantity}</td>
                                </tr>
                            </c:forEach>
                            <c:if test="${empty inventoryItems}">
                                <tr><td colspan="8" class="empty">Không có dữ liệu</td></tr>
                            </c:if>
                        </tbody>
                    </table>
                </c:when>

                <c:when test="${reportType == 'import'}">
                    <c:if test="${not empty trendJson}">
                    <div class="rpt-chart"><canvas id="chart1" height="80"></canvas></div>
                    <script>RPT.charts=RPT.charts||[];RPT.charts.push({id:'chart1',data:${trendJson},report:'import_'});</script>
                    </c:if>
                    <table class="report-table">
                        <thead>
                            <tr>
                                <th>STT</th>
                                <th>Máy</th>
                                <th>Mẫu máy</th>
                                <th>Mã phiếu</th>
                                <th>Mã đơn mua</th>
                                <th>Ngày</th>
                                <th>Kho</th>
                                <th>Người tạo</th>
                            </tr>
                        </thead>
                        <tbody>
                            <c:forEach var="r" items="${importItems}" varStatus="st">
                                <tr>
                                    <td>${st.index + 1 + (currentPage - 1) * 15}</td>
                                    <td><c:out value="${r.serialNumber}"/></td>
                                    <td><c:out value="${r.model}"/></td>
                                    <td><a href="${pageContext.request.contextPath}/import-receipt?action=detail&id=${r.receiptId}" class="code-link"><c:out value="${r.receiptCode}"/></a></td>
                                    <td><c:choose><c:when test="${not empty r.purchaseOrderId}"><a href="${pageContext.request.contextPath}/purchase-order?action=detail&id=${r.purchaseOrderId}" class="code-link"><c:out value="${r.purchaseOrderCode}"/></a></c:when><c:otherwise><c:out value="${r.purchaseOrderCode}"/></c:otherwise></c:choose></td>
                                    <td>${r.createdAt.format(rptFmt)}</td>
                                    <td><c:out value="${r.warehouseName}"/></td>
                                    <td><c:out value="${r.createdByName}"/></td>
                                </tr>
                            </c:forEach>
                            <c:if test="${empty importItems}">
                                <tr><td colspan="8" class="empty">Không có dữ liệu</td></tr>
                            </c:if>
                        </tbody>
                    </table>
                </c:when>

                <c:when test="${reportType == 'export'}">
                    <c:if test="${not empty trendJson}">
                    <div class="rpt-chart"><canvas id="chart2" height="80"></canvas></div>
                    <script>RPT.charts=RPT.charts||[];RPT.charts.push({id:'chart2',data:${trendJson},report:'export_'});</script>
                    </c:if>
                    <table class="report-table">
                        <thead>
                            <tr>
                                <th>STT</th>
                                <th>Máy</th>
                                <th>Mẫu máy</th>
                                <th>Mã phiếu</th>
                                <th>Mã đơn hàng</th>
                                <th>Ngày</th>
                                <th>Kho</th>
                                <th>Người tạo</th>
                            </tr>
                        </thead>
                        <tbody>
                            <c:forEach var="r" items="${exportItems}" varStatus="st">
                                <tr>
                                    <td>${st.index + 1 + (currentPage - 1) * 15}</td>
                                    <td><c:out value="${r.serialNumber}"/></td>
                                    <td><c:out value="${r.model}"/></td>
                                    <td><a href="${pageContext.request.contextPath}/export-receipt?action=detail&id=${r.receiptId}" class="code-link"><c:out value="${r.receiptCode}"/></a></td>
                                    <td><c:choose><c:when test="${not empty r.orderId}"><a href="${pageContext.request.contextPath}/order?action=detail&id=${r.orderId}" class="code-link"><c:out value="${r.orderCode}"/></a></c:when><c:otherwise><c:out value="${r.orderCode}"/></c:otherwise></c:choose></td>
                                    <td>${r.createdAt.format(rptFmt)}</td>
                                    <td><c:out value="${r.warehouseName}"/></td>
                                    <td><c:out value="${r.createdByName}"/></td>
                                </tr>
                            </c:forEach>
                            <c:if test="${empty exportItems}">
                                <tr><td colspan="8" class="empty">Không có dữ liệu</td></tr>
                            </c:if>
                        </tbody>
                    </table>
                </c:when>

                <c:when test="${reportType == 'purchase'}">
                    <div class="summary-cards">
                        <div class="summary-card">
                            <div class="summary-label">Tổng tiền mua hàng</div>
                            <div class="summary-value"><fmt:formatNumber value="${summary.totalAmount}" pattern="#,##0"/> ₫</div>
                        </div>
                        <div class="summary-card">
                            <div class="summary-label">Mẫu máy mua nhiều nhất</div>
                            <div class="summary-value"><c:out value="${summary.topModel}"/></div>
                            <div class="summary-sub">${summary.topModelQty} máy</div>
                        </div>
                    </div>
                    <c:if test="${not empty trendJson}">
                    <div class="rpt-chart"><canvas id="chart4" height="80"></canvas></div>
                    <script>RPT.charts=RPT.charts||[];RPT.charts.push({id:'chart4',data:${trendJson},report:'purchase'});</script>
                    </c:if>
                    <table class="report-table">
                        <thead>
                            <tr>
                                <th>STT</th>
                                <th>Mã phiếu</th>
                                <th>Kho</th>
                                <th>Nhà cung cấp</th>
                                <th>Số lượng</th>
                                <th>Tổng tiền</th>
                                <th>Người tạo</th>
                                <th>Ngày tạo</th>
                            </tr>
                        </thead>
                        <tbody>
                            <c:forEach var="po" items="${poItems}" varStatus="st">
                                <tr>
                                    <td>${st.index + 1 + (currentPage - 1) * 15}</td>
                                    <td><a href="${pageContext.request.contextPath}/purchase-order?action=detail&id=${po.poId}" class="code-link"><c:out value="${po.poCode}"/></a></td>
                                    <td><c:out value="${po.warehouseName}"/></td>
                                    <td><c:out value="${not empty po.supplierName ? po.supplierName : '—'}"/></td>
                                    <td class="num">${po.totalQuantity}</td>
                                    <td class="num"><fmt:formatNumber value="${po.totalAmount}" pattern="#,##0"/> ₫</td>
                                    <td><c:out value="${po.createdByName}"/></td>
                                    <td>${po.createdAt.format(rptFmt)}</td>
                                </tr>
                            </c:forEach>
                            <c:if test="${empty poItems}">
                                <tr><td colspan="8" class="empty">Không có dữ liệu</td></tr>
                            </c:if>
                        </tbody>
                    </table>
                </c:when>
                
                <c:when test="${reportType == 'sales'}">
                    <div class="summary-cards">
                        <div class="summary-card">
                            <div class="summary-label">Tổng doanh thu bán hàng</div>
                            <div class="summary-value"><fmt:formatNumber value="${summary.totalAmount}" pattern="#,##0"/> ₫</div>
                        </div>
                        <div class="summary-card">
                            <div class="summary-label">Máy bán nhiều nhất</div>
                            <div class="summary-value"><c:out value="${summary.topModel}"/></div>
                            <div class="summary-sub">${summary.topModelQty} máy</div>
                        </div>
                    </div>
                    <c:if test="${not empty trendJson}">
                    <div class="rpt-chart"><canvas id="chart5" height="80"></canvas></div>
                    <script>RPT.charts=RPT.charts||[];RPT.charts.push({id:'chart5',data:${trendJson},report:'sales'});</script>
                    </c:if>
                    <table class="report-table">
                        <thead>
                            <tr>
                                <th>STT</th>
                                <th>Mã phiếu</th>
                                <th>Kho</th>
                                <th>Khách hàng</th>
                                <th>Số lượng</th>
                                <th>Tổng tiền</th>
                                <th>Người tạo</th>
                                <th>Ngày tạo</th>
                            </tr>
                        </thead>
                        <tbody>
                            <c:forEach var="so" items="${saleItems}" varStatus="st">
                                <tr>
                                    <td>${st.index + 1 + (currentPage - 1) * 15}</td>
                                    <td><a href="${pageContext.request.contextPath}/order?action=detail&id=${so.orderId}" class="code-link"><c:out value="${so.orderCode}"/></a></td>
                                    <td><c:out value="${so.warehouseName}"/></td>
                                    <td><c:out value="${so.customer.name}"/></td>
                                    <td class="num">${so.totalQuantity}</td>
                                    <td class="num"><fmt:formatNumber value="${so.totalAmount}" pattern="#,##0"/> ₫</td>
                                    <td><c:out value="${so.createdByName}"/></td>
                                    <td><fmt:formatDate value="${so.createdAt}" pattern="dd/MM/yyyy"/></td>
                                </tr>
                            </c:forEach>
                            <c:if test="${empty saleItems}">
                                <tr><td colspan="8" class="empty">Không có dữ liệu</td></tr>
                            </c:if>
                        </tbody>
                    </table>
                </c:when>
            </c:choose>
            <c:if test="${totalPages > 1}">
                <c:set var="q" value="type=${reportType}&month=${month}&year=${year}&warehouseId=${selWarehouseId}"/>
                <c:if test="${not empty search}"><c:set var="q" value="${q}&search=${fn:escapeXml(search)}"/></c:if>
                <div class="pagination">
                    <div class="info">Hiển thị <strong>${(currentPage - 1) * 15 + 1}</strong>–<strong>${currentPage * 15 > totalItems ? totalItems : currentPage * 15}</strong> / <strong>${totalItems}</strong> kết quả</div>
                    <div class="controls">
                        <c:if test="${currentPage > 1}">
                            <a href="?${q}&page=${currentPage - 1}" class="page-btn">‹</a>
                        </c:if>
                        <c:forEach begin="1" end="${totalPages}" var="p">
                            <c:choose>
                                <c:when test="${p == currentPage}"><span class="page-btn active">${p}</span></c:when>
                                <c:otherwise><a href="?${q}&page=${p}" class="page-btn">${p}</a></c:otherwise>
                            </c:choose>
                        </c:forEach>
                        <c:if test="${currentPage < totalPages}">
                            <a href="?${q}&page=${currentPage + 1}" class="page-btn">›</a>
                        </c:if>
                    </div>
                </div>
            </c:if>
        </div>
    </main>
</div>

<script>
RPT.ctx = '${pageContext.request.contextPath}';
RPT.type = '${reportType}';
RPT.month = ${month};
RPT.year = ${year};
RPT.warehouseId = '${selWarehouseId}';
RPT.initCharts();
</script>
</body>
</html>