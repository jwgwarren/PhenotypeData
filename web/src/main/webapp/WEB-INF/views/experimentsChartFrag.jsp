<%@taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@taglib prefix="t" tagdir="/WEB-INF/tags" %>

<div id="phChart">
    <c:if test="${chart != null}">
        <p class="alert alert-info w-100 mt-2">Mouseover the data points for more information. Click and drag to zoom the chart. Click on the legends to disable/enable data.</p>
        <button id="checkAll" class="btn btn-sm btn-outline-success"><i class="fa fa-check" aria-hidden="true"></i> Select all</button>
        <button id="uncheckAll" class="btn btn-sm btn-outline-danger"><i class="fa fa-times" aria-hidden="true"></i> Deselect all</button>
        <div id="chartDiv"></div>
        <div class="clear both"></div>

        <script type="text/javascript" async>${chart}</script>
    </c:if>
    <c:if test="${chart == null}">
        <p class="alert alert-warning w-100 mt-2">Statistical analisys has not been perform on any of the selected systems. Check the table view to see the raw data.</p>
    </c:if>
</div>
<script>
    $(document).ready(function () {
        $('#allDataChartCount').html(${count});
    });
</script>
