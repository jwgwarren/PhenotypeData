<%--
  Created by IntelliJ IDEA.
  User: ckc
  Date: 23/02/2016
  Time: 10:37
  To change this template use File | Settings | File Templates.
--%>


<%@taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@taglib prefix="t" tagdir="/WEB-INF/tags" %>


<!--  login interest button -->
<div class="floatright">
  <c:choose>
    <c:when
            test="${registerButtonAnchor!=''}">
      <p><a class="btn"
            href='${registerButtonAnchor}'><i
              class="fa fa-sign-in"></i>${registerInterestButtonString}</a>
      </p>
    </c:when>
    <c:otherwise>
      <p><a
              class="btn interest" id='${registerButtonId}'><i
              class="fa fa-sign-in"></i>${registerInterestButtonString}</a>
      </p>
    </c:otherwise>
  </c:choose>
  <c:if
          test="${orderPossible}">
    <p><a class="btn"
          href="#order2"> <i class="fa fa-shopping-cart"></i> Order </a></p>
  </c:if>

</div>

<c:if test="${gene.markerName != null}">
  <p class="with-label no-margin">
    <span class="label">Name</span>
      ${gene.markerName}
  </p>
</c:if>

<c:if
        test="${!(empty gene.markerSynonym)}">
  <p class="with-label no-margin">
    <span class="label">Synonyms</span>
    <c:forEach var="synonym" items="${gene.markerSynonym}" varStatus="loop">
      ${synonym}
      <c:if test="${!loop.last}">, </c:if>
      <c:if test="${loop.last}"></c:if>
    </c:forEach>
  </p>
</c:if>

<p class="with-label">
  <span class="label">MGI Id</span>
  <a target="_blank" href="http://www.informatics.jax.org/marker/${gene.mgiAccessionId}">${gene.mgiAccessionId}</a>
</p>

<c:if
        test="${!(prodStatusIcons == '')}">
  <p class="with-label">
    <span class="label">Status</span>
      ${prodStatusIcons}
  </p>
</c:if>

<p class="with-label">
  <span class="label">Links</span>
  <a target="_blank" href="http://www.ensembl.org/Mus_musculus/Gene/Summary?g=${gene.mgiAccessionId}">Ensembl
    Gene</a>&nbsp;&nbsp;
  <!--    <a href="http://www.ensembl.org/Mus_musculus/Location/View?g=${gene.mgiAccessionId};contigviewbottom=das:http://das.sanger.ac.uk/das/ikmc_products=labels">Location&nbsp;View</a>&nbsp;&nbsp;-->
  <a target="_blank" href="http://www.ensembl.org/Mus_musculus/Location/Compara_Alignments/Image?align=677;db=core;g=${gene.mgiAccessionId}">Ensembl
    Compara</a>
  &nbsp;<a href="../genomeBrowser/${acc}" target="new"> IMPC Gene Browser</a><span
        id="enu"></span>
</p>

<c:if test="${viabilityCalls != null && viabilityCalls.size() > 0}">
  <p class="with-label">
    <span class="label">Viability</span>
    <t:viabilityButton callList="${viabilityCalls}" link=""></t:viabilityButton>
  </p>
</c:if>

<!-- show GWAS stuff for now -->
<c:if test="${gwasPhenoMapping != null }">

  <c:if test="${gwasPhenoMapping == 'no mapping' }">
    <p class="with-label">
    <span class="label">GWAS mapping</span>
    <a target="_blank" href="http://www.ebi.ac.uk/gwas/search?query=${gene.markerSymbol}"><i class="fa fa-external-link"></i>&nbsp;GWAS catalog</a>&nbsp;&nbsp;
    </p>
  </c:if>
  <c:if test="${gwasPhenoMapping == 'indirect' }">
    <p class="with-label">
    <span class="label">GWAS mapping</span>
    <a href="http://www.ebi.ac.uk/gwas/search?query=${gene.markerSymbol}"><i class="fa fa-external-link"></i>&nbsp;GWAS catalog</a>&nbsp;&nbsp;
    <a target="_blank" href="${baseUrl}/phenotype2gwas?mgi_gene_symbol=${gene.markerSymbol}"><i class="fa fa-external-link"></i>&nbsp;<span class='indirect'>${gwasPhenoMapping} phenotypic mapping</span></a>&nbsp;&nbsp;

    </p>
  </c:if>
  <c:if test="${gwasPhenoMapping == 'direct' }">
    <p class="with-label">
    <span class="label">GWAS mapping</span>
    <a href="http://www.ebi.ac.uk/gwas/search?query=${gene.markerSymbol}"><i class="fa fa-external-link"></i>&nbsp;GWAS catalog</a>&nbsp;&nbsp;
    <a target="_blank" href="${baseUrl}/phenotype2gwas?mgi_gene_symbol=${gene.markerSymbol}"><i class="fa fa-external-link"></i>&nbsp;<span class='direct'>${gwasPhenoMapping} phenotypic mapping</span></a>&nbsp;&nbsp;
    </p>
  </c:if>

</c:if>


