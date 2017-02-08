<%@ page language="java" contentType="text/html; charset=UTF-8"
         pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="t" tagdir="/WEB-INF/tags" %>
<%@ taglib prefix='fn' uri='http://java.sun.com/jsp/jstl/functions' %>

<t:genericpage>

    <jsp:attribute name="title">IDG | IMPC Project Information</jsp:attribute>
    <jsp:attribute name="breadcrumb">&nbsp;&raquo; <a
            href="${baseUrl}/IDG">IDG</a> &raquo; IDG</jsp:attribute>
    <jsp:attribute name="bodyTag">
		<body class="chartpage no-sidebars small-header">
	</jsp:attribute>
    <jsp:attribute name="header">
		<script type='text/javascript' src='${baseUrl}/js/charts/highcharts.js?v=${version}'></script>
        <script type='text/javascript' src='${baseUrl}/js/charts/highcharts-more.js?v=${version}'></script>
        <script type='text/javascript' src='${baseUrl}/js/charts/exporting.js?v=${version}'></script><script
            type="text/javascript" src="${baseUrl}/js/charts/chordDiagram.js?v=${version}"></script>
        <script src="//d3js.org/queue.v1.min.js"></script>
        <script src="//d3js.org/d3.v4.min.js"></script>
    </jsp:attribute>

    <jsp:attribute name="addToFooter">

		<div class="region region-pinned">
            <div id="flyingnavi" class="block smoothScroll">
                <a href="#top"><i class="fa fa-chevron-up"
                                  title="scroll to top"></i></a>
                <ul>
                    <li><a href="#top">IDG</a></li>
                </ul>
                <div class="clear"></div>
            </div>
        </div>

    </jsp:attribute>


    <jsp:body>
        <!-- Assign this as a variable for other components -->
        <script type="text/javascript">
            var base_url = '${baseUrl}';
        </script>


        		<div class="section">
                    <h1 class="title">Illuminating the Druggable Genome (IDG)</h1>
					<div class=inner>
						<div class="floatright">
							<img src="${baseUrl}/img/idgLogo.png" height="85" width="130">
						</div>
						<p>
                            IDG (link to ) is an NIH Common Fund project focused on collecting, integrating and making available biological data on 395
                            human genes from three key druggable protein families that have been identified as potential therapeutic targets: non-olfactory
                            G-protein coupled receptors (GPCRs), ion channels, and protein kinases. The <a href="http://www.mousephenotype.org/data/documentation/aboutImpc#whatisimpc">IMPC consortium</a>
                            is creating where possible knockout mouse strains for the IDG project to better understand the function of these proteins.
						</p>
                    </div>
                </div>	<!-- section -->


						<div class="section" >
								<h1 id="section-associations">IMPC data representation for IDG genes</h1>
		           					 <div class="inner">
		            	<p>
                            We identified Human to mouse IDG orthologs (link to pie chart) using HomoloGene
                            (no link put a link in the IDG ortholog page). The IMPC consortium is using different <a href="link to http://www.mousephenotype.org/data/documentation/aboutImpc#howdoesimpcwork">complementary targeting strategies</a>
                            to produce Knockout strains. Mice are produced and submitted to <a href="http://www.mousephenotype.org/data/documentation/aboutImpc#whatdoesimpcdo">standardised phenotyping pipelines</a>. About 77.4 % of mouse
                            IDG gene have data representation in IMPC, the graph and heatmap below capture the IMPC data representation at different levels.
		            	</p>

		            	<h3>IDG gene IMPC production status</h3>
									<div  class="half">
										<div id="idgOrthologPie">
			            		<script type="text/javascript">
													${idgOrthologPie}
											</script>
										</div>
									</div>
									<div  class="half">
			            	<div id=idgChart>
			            		<script type="text/javascript">
													${idgChartTable.getChart()}
											</script>
										</div>
									</div>
		            	  <div class="clear"></div>
		            </div>

		        </div> <!-- section -->

        <div class="section">

            <h1 >IMPC IDG data Heat Map</h1>

            <div class=inner>
                <p>
                    The heat map indicate the detailed IDG gene data representation in IMPC, from IMPC product availability to phenotypes.
                </p>
                <div id="geneHeatmap" class="geneHeatMap" style="overflow: hidden; overflow-x: auto;">
                </div>
            </div>

        </div>

        <div class="section" id="phenotypePValueDistribution">
            <h1 id="section-associations">Phenotype Associations</h1>
            <div class="inner">
                <p>The following chord diagrams represent the various biological system phenotypes (link to all biological systems) associations for IDG genes categorized both in all and in each family group. The line thickness is correlated with the strength of the association.
                    Clicking on chosen phenotype(s) on the diagram allow to select common genes. Corresponding gene lists can be downloaded using the download icon.</p>
                <h3>All</h3>
                <div id="chordContainer" class="half"></div>
                <svg id="chordDiagramSvg" width="960" height="960"></svg>
                <script>
                    drawChords("chordDiagramSvg", "chordContainer", false, null, true, null);
                </script>

                <h3>Ion channels</h3>
                <div id="chordContainerIonChannels" class="half"></div>
                <svg id="chordDiagramSvgIonChannels" width="960" height="960"></svg>
                <script>
                    drawChords("chordDiagramSvgIonChannels", "chordContainerIonChannels", false, null, true, "Ion Channels");
                </script>


                <h3>GPCRs</h3>
                <div id="chordContainerGPCRs" class="half"></div>
                <svg id="chordDiagramSvgGPCRs" width="960" height="960"></svg>
                <script>
                    drawChords("chordDiagramSvgGPCRs", "chordContainerGPCRs", false, null, true, "GPCRs");
                </script>


                <h3>Kinases</h3>
                <div id="chordContainerKinases" class="half"></div>
                <svg id="chordDiagramSvgKinases" width="960" height="960"></svg>
                <script>
                    drawChords("chordDiagramSvgKinases", "chordContainerKinases", false, null, true, "Kinases");
                </script>
            </div>
        </div>
        <!-- section -->

        <script>
            $(document).ready(function () {
                $.fn.qTip({
                    'pageName': 'idg',
                    'textAlign': 'left',
                    'tip': 'topLeft'
                }); // bubble popup for brief panel documentation
            });
            var geneHeatmapUrl = "../geneHeatMap?project=idg";
            $.ajax({
                url: geneHeatmapUrl,
                cache: false
            }).done(function (html) {
                $('#geneHeatmap').append(html);
                //$( '#spinner'+ id ).html('');

            });
        </script>

    </jsp:body>


</t:genericpage>