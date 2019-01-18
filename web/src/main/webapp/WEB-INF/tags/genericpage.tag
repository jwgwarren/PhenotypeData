<%@ tag description="Overall Page template" pageEncoding="UTF-8" import="uk.ac.ebi.phenotype.web.util.DrupalHttpProxy,java.net.URLEncoder" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn" %>
<%@ taglib prefix="t" tagdir="/WEB-INF/tags"%>


<%-- -------------------------------------------------------------------------- --%>
<%-- NOTE: All "magic" variables are defined in the DeploymentInterceptor class --%>
<%-- This includes such variables isbaseUrl, drupalBaseUrl and releaseVersion.. --%>
<%-- -------------------------------------------------------------------------- --%>


<%
    /*
     Get the menu JSON array from drupal, fallback to a default menu when drupal
     cannot be contacted
     */
    DrupalHttpProxy proxy = new DrupalHttpProxy(request);
    String url = (String) request.getAttribute("drupalBaseUrl");

    String content = proxy.getDrupalMenu(url);
    String[] menus = content.split("MAIN\\*MENU\\*BELOW");

    String baseUrl = (request.getAttribute("baseUrl") != null &&  ! ((String) request.getAttribute("baseUrl")).isEmpty()) ? (String) request.getAttribute("baseUrl") : (String) application.getInitParameter("baseUrl");
    jspContext.setAttribute("baseUrl", baseUrl);



    // Use the drupal destination parameter to redirect back to this page
    // after logging in
    String dest = (String) request.getAttribute("javax.servlet.forward.request_uri");
    String destUnEncoded = dest;
    if (request.getQueryString() != null) {
        dest += URLEncoder.encode("?" + request.getQueryString(), "UTF-8");
        destUnEncoded += "?" + request.getQueryString();
    }

    String usermenu = menus[0]
            .replace("current=menudisplaycombinedrendered", "destination=" + dest)
            .replace("user/register", "user/register?destination=" + dest)
            .replace(request.getContextPath(), baseUrl.substring(1));

    jspContext.setAttribute("usermenu", usermenu);
    jspContext.setAttribute("menu", menus[1]);
%>
<%@attribute name="header" fragment="true"%>
<%@attribute name="footer" fragment="true"%>
<%@attribute name="title" fragment="true"%>
<%@attribute name="breadcrumb" fragment="true"%>
<%@attribute name="bodyTag" fragment="true"%>
<%@attribute name="addToFooter" fragment="true"%>

<c:set var="uri">${pageContext.request.requestURL}</c:set>
<c:set var="domain">${pageContext.request.serverName}</c:set>

<c:set var="queryStringPlaceholder">
    <c:choose>
        <c:when test="${not empty queryString}">${queryString}</c:when>
        <c:otherwise>Search genes, SOP, MP, images by MGI ID, gene symbol, synonym or name</c:otherwise>
    </c:choose>
</c:set>
<!doctype html>
<html lang="en">

<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1, shrink-to-fit=no">
    <title><jsp:invoke fragment="title"></jsp:invoke> | International Mouse Phenotyping Consortium</title>
    <link rel="profile" href="http://gmpg.org/xfn/11">
    <link rel="pingback" href="https://mousephenotypedev.org/xmlrpc.php">
   <!--  <link rel="stylesheet" href="https://pro.fontawesome.com/releases/v5.6.3/css/all.css" integrity="FA-SHA"
          crossorigin="anonymous"> -->
    <link rel="apple-touch-icon-precomposed" sizes="57x57" href="${baseUrl}/img/apple-touch-icon-57x57.png" />
    <link rel="apple-touch-icon-precomposed" sizes="114x114" href="${baseUrl}/img/apple-touch-icon-114x114.png" />
    <link rel="apple-touch-icon-precomposed" sizes="72x72" href="${baseUrl}/img/apple-touch-icon-72x72.png" />
    <link rel="apple-touch-icon-precomposed" sizes="144x144" href="${baseUrl}/img/apple-touch-icon-144x144.png" />
    <link rel="apple-touch-icon-precomposed" sizes="60x60" href="${baseUrl}/img/apple-touch-icon-60x60.png" />
    <link rel="apple-touch-icon-precomposed" sizes="120x120" href="${baseUrl}/img/apple-touch-icon-120x120.png" />
    <link rel="apple-touch-icon-precomposed" sizes="76x76" href="${baseUrl}/img/apple-touch-icon-76x76.png" />
    <link rel="apple-touch-icon-precomposed" sizes="152x152" href="${baseUrl}/img/apple-touch-icon-152x152.png" />
    <link rel="icon" type="image/png" href="${baseUrl}/img/favicon-196x196.png" sizes="196x196" />
    <link rel="icon" type="image/png" href="${baseUrl}/img/favicon-96x96.png" sizes="96x96" />
    <link rel="icon" type="image/png" href="${baseUrl}/img/favicon-32x32.png" sizes="32x32" />
    <link rel="icon" type="image/png" href="${baseUrl}/img/favicon-16x16.png" sizes="16x16" />
    <link rel="icon" type="image/png" href="${baseUrl}/img/favicon-128.png" sizes="128x128" />
    <meta name="msapplication-TileColor" content="#FFFFFF" />
    <meta name="msapplication-TileImage" content="${baseUrl}/img/mstile-144x144.png" />
    <meta name="msapplication-square70x70logo" content="${baseUrl}/img/mstile-70x70.png" />
    <meta name="msapplication-square150x150logo" content="${baseUrl}/img/mstile-150x150.png" />
    <meta name="msapplication-wide310x150logo" content="${baseUrl}/img/mstile-310x150.png" />
    <meta name="msapplication-square310x310logo" content="${baseUrl}/img/mstile-310x310.png" />
    <script type='text/javascript' src='https://cdnjs.cloudflare.com/ajax/libs/jquery/3.3.1/jquery.min.js'></script>
    <!-- <script type="text/javascript" src="//ajax.googleapis.com/ajax/libs/jquery/1.10.2/jquery.min.js"></script>
    <script src="http://code.jquery.com/jquery-migrate-1.4.1.js"></script> -->
    <script type='text/javascript' src='https://cdnjs.cloudflare.com/ajax/libs/twitter-bootstrap/4.1.3/js/bootstrap.bundle.min.js'
            async='async'></script>
    <script type='text/javascript' src='https://cdnjs.cloudflare.com/ajax/libs/object-fit-images/3.2.4/ofi.min.js'></script>
    <script type='text/javascript' src='https://cdnjs.cloudflare.com/ajax/libs/prefixfree/1.0.7/prefixfree.min.js'></script>
    <link rel="stylesheet" type="text/css" href="https://cdn.datatables.net/v/bs4/dt-1.10.18/r-2.2.2/datatables.min.css"/>
    <script src="https://stackpath.bootstrapcdn.com/bootstrap/4.2.1/js/bootstrap.min.js" integrity="sha384-B0UglyR+jN6CkvvICOB2joaf5I4l3gm9GU6Hc1og6Ls7i6U/mkkaduKaBhlAXv9k" crossorigin="anonymous"></script>
    <script type="text/javascript" src="https://cdn.datatables.net/v/bs4/dt-1.10.18/r-2.2.2/datatables.min.js"></script>
    <script
            src="https://code.jquery.com/ui/1.12.0/jquery-ui.min.js"
            integrity="sha256-eGE6blurk5sHj+rmkfsGYeKyZx3M4bG+ZlFyA7Kns7E="
            crossorigin="anonymous"></script>




    <script type='text/javascript' src="${baseUrl}/js/general/toggle.js?v=${version}"></script>
    <script type="text/javascript" src="${baseUrl}/js/head.min.js?v=${version}"></script>
    <script type='text/javascript' src='${baseUrl}/js/buffaloZoo.js'></script>
    <script type="text/javascript" src="${baseUrl}/js/default.js?v=${version}"></script>
    <link rel="stylesheet" href="${baseUrl}/js/vendor/jquery/jquery.qtip-2.2/jquery.qtip.min.css">
    <link rel="stylesheet" href="https://cdn.jsdelivr.net/gh/fancyapps/fancybox@3.5.6/dist/jquery.fancybox.min.css" />
    <script src="https://cdn.jsdelivr.net/gh/fancyapps/fancybox@3.5.6/dist/jquery.fancybox.min.js"></script>
    <link rel="stylesheet" type="text/css" href="https://cdn.datatables.net/1.10.19/css/dataTables.bootstrap4.min.css">
    <!-- <script src="https://cdnjs.cloudflare.com/ajax/libs/popper.js/1.14.6/umd/popper.min.js" integrity="sha384-wHAiFfRlMFy6i5SRaxvfOCifBUQy1xHdJ/yoi7FRNXMRBu5WHdZYu1hA6ZOblgut" crossorigin="anonymous"></script>
     replaced with below as unable to get his due to CORS or licence?-->
     
    <script src="https://cdnjs.cloudflare.com/ajax/libs/popper.js/1.11.0/umd/popper.min.js"></script>
    <link href="${baseUrl}/css/default.css" rel="stylesheet" type="text/css" media='all'/>
    <link href="${baseUrl}/css/additionalStyling.css" rel="stylesheet" type="text/css" />
    <link href="${baseUrl}/css/impc-icons.css" rel="stylesheet" type="text/css" />
    <link rel="stylesheet" type="text/css" href="https://ebi.emblstatic.net/web_guidelines/EBI-Icon-fonts/v1.3/fonts.css">
    <jsp:invoke fragment="header" />

</head>

<body>
<jsp:invoke fragment="bodyTag"/>
<div class="header">
    <div class="header__nav-top d-none d-lg-block">
        <div class="container text-right">
            <div class="menu-top-nav-container">
                <ul id="menu-top-nav" class="menu">
                    <li id="menu-item-13" class="menu-item menu-item-type-post_type menu-item-object-page page_item page-item-11 menu-item-13"><a
                            href="https://mousephenotypedev.org/faqs/">FAQs</a></li>
                    <li id="menu-item-14" class="menu-item menu-item-type-custom menu-item-object-custom menu-item-14"><a
                            href="#">Forum</a></li>
                    <li id="menu-item-15" class="menu-item menu-item-type-post_type menu-item-object-page menu-item-15"><a
                            href="https://mousephenotypedev.org/contact-us/">Contact Us</a></li>
                </ul>
            </div> <a id="searchButton" class="header__search collapsed" href="/" data-toggle="collapse"
                      data-target="#searchBar" aria-controls="searchBar" aria-expanded="false">Search<i id="search-icon-open"
                                                                                                        class="fal fa-search"></i><i id="search-icon-close" class="fal fa-times"></i></a>
        </div>
    </div>

    <div class="header__nav">
        <div class="container">
            <div class="row">
                <div class="col-6 col-md-3">
                    <a href="https://mousephenotypedev.org" class="header__logo-link "><img class="header__logo" src="${baseUrl}/img/impclogo@2x.png" /></a>
                </div>
                <div class="col-6 col-md-9 text-right">
                        <span class="d-none d-lg-block">
                            <div class="menu-main-nav-container">
                                <ul id="menu-main-nav" class="menu">
                                    <li id="menu-item-16" class="menu-item menu-item-type-post_type menu-item-object-page menu-item-16"><a
                                            href="https://mousephenotypedev.org/about-impc/">About IMPC</a></li>
                                    <li id="menu-item-17" class="menu-item menu-item-type-post_type menu-item-object-page menu-item-17"><a
                                            href="https://mousephenotypedev.org/understanding-the-data/">Understanding
                                            the Data</a></li>
                                    <li id="menu-item-18" class="menu-item menu-item-type-post_type menu-item-object-page menu-item-18"><a
                                            href="https://mousephenotypedev.org/human-diseases/">Human Diseases</a></li>
                                    <li id="menu-item-19" class="menu-item menu-item-type-post_type menu-item-object-page menu-item-19"><a
                                            href="https://mousephenotypedev.org/news-and-events/">News &#038; Events</a></li>
                                    <li id="menu-item-20" class="menu-item menu-item-type-custom menu-item-object-custom menu-item-20"><a
                                            href="#">Blog</a></li>
                                </ul>
                            </div>
                        </span>
                    <button class="navbar-toggler collapsed d-inline d-lg-none active" type="button" data-toggle="collapse"
                            data-target="#navbarToggleExternalContent " aria-controls="navbarToggleExternalContent"
                            aria-expanded="false" aria-label="Toggle navigation">
                        <span class="icon-bar top-bar"></span>
                        <span class="icon-bar middle-bar"></span>
                        <span class="icon-bar bottom-bar"></span>
                    </button>
                    <div class="collapse" id="searchBar">
                        <form action="https://mousephenotypedev.org">
                            <div class="row search-pop">
                                <div class="search-pop__input col col-9 text-left no-gutters">
                                    <p>This search is for the main website only. Please use the specialist portal
                                        search for Genes and
                                        Phenotypes.</p>
                                    <input id="searchField" type="search" class="form-control" id="s" name="s"
                                           placeholder="Let's find what I'm looking for...">
                                </div>
                                <div class="col col-3 text-right search-submit">
                                    <button type="submit">Search <i class="fal fa-search"></i></button>
                                </div>
                            </div>
                        </form>
                    </div>

                </div>
            </div>
        </div>

    </div>
    <div class="header__drop"></div>

    <div class="mobile-nav collapse" id="navbarToggleExternalContent">
        <button class="navbar-toggler" type="button" data-toggle="collapse" data-target="#navbarToggleExternalContent "
                aria-controls="navbarToggleExternalContent" aria-expanded="false" aria-label="Toggle navigation">
            <span class="icon-bar top-bar"></span>
            <span class="icon-bar middle-bar"></span>
            <span class="icon-bar bottom-bar"></span>
        </button>
        <p class="mobile-nav__search-text">This search is for the main website only. Please use the specialist
            portal
            search for Genes and Phenotypes.</p>
        <div class="mobile-nav__search mb-3">
            <form action="https://mousephenotypedev.org">
                <div class="row">
                    <div class="col col-10 text-left">
                        <input type="search" class="form-control" id="s" name="s" placeholder="I'm looking for...">
                    </div>
                    <div class="col col-2 text-right">
                        <button type="submit"> <i class="fas fa-search"></i></button>
                    </div>
                </div>
            </form>
        </div>
        <div class="row">
            <div class="col-12">

                <h3 class="mt-2"><a class="object-id-7" href="https://mousephenotypedev.org/about-impc/">About IMPC</a></h3>

                <div class="mobile-nav__sub-pages">
                    <p><a href="https://mousephenotypedev.org/about-impc/collaborations/">Collaborations</a></p>
                    <div class="sub-pages">
                    </div>
                    <p><a href="https://mousephenotypedev.org/about-impc/funding/">Funding</a></p>
                    <div class="sub-pages">
                    </div>
                    <p><a href="https://mousephenotypedev.org/about-impc/consortium-members/">Consortium members</a></p>
                    <div class="sub-pages">
                    </div>
                    <p><a href="https://mousephenotypedev.org/about-impc/animal-welfare/">Animal welfare</a></p>
                    <div class="sub-pages">
                    </div>
                    <p><a href="https://mousephenotypedev.org/about-impc/governance/">Governance</a></p>
                    <div class="sub-pages">
                    </div>
                    <p><a href="https://mousephenotypedev.org/about-impc/our-people/">Our people</a></p>
                    <div class="sub-pages">
                    </div>

                </div>

                <h3 class="mt-2"><a class="object-id-8" href="https://mousephenotypedev.org/understanding-the-data/">Understanding
                    the Data</a></h3>

                <div class="mobile-nav__sub-pages">
                    <p><a href="https://mousephenotypedev.org/understanding-the-data/gene-knockout-technology/">Gene
                        knockout technology</a></p>
                    <div class="sub-pages">
                    </div>
                    <p><a href="https://mousephenotypedev.org/understanding-the-data/research-highlights/">Research
                        highlights</a></p>
                    <div class="sub-pages">
                        <p><a href="https://mousephenotypedev.org/understanding-the-data/research-highlights/vignettes/">Vignettes</a></p>

                        <p><a href="https://mousephenotypedev.org/understanding-the-data/research-highlights/embryo-development/">Embryo
                            development</a></p>

                        <p><a href="https://mousephenotypedev.org/understanding-the-data/research-highlights/sexual-dimorphism/">Sexual
                            dimorphism</a></p>

                        <p><a href="https://mousephenotypedev.org/understanding-the-data/research-highlights/cardiovascular/">Cardiovascular</a></p>

                        <p><a href="https://mousephenotypedev.org/understanding-the-data/research-highlights/hearing/">Hearing</a></p>

                        <p><a href="https://mousephenotypedev.org/understanding-the-data/research-highlights/metabolism/">Metabolism</a></p>

                        <p><a href="https://mousephenotypedev.org/understanding-the-data/research-highlights/translating-to-other-species/">Translating
                            to other species</a></p>

                        <p><a href="https://mousephenotypedev.org/understanding-the-data/research-highlights/embryo-development-2/">Embryo
                            development</a></p>

                    </div>
                    <p><a href="https://mousephenotypedev.org/understanding-the-data/latest-data-release/">Latest
                        data
                        release</a></p>
                    <div class="sub-pages">
                    </div>
                    <p><a href="https://mousephenotypedev.org/understanding-the-data/phenotyping-process-impress/">Phenotyping
                        process (IMPRess)</a></p>
                    <div class="sub-pages">
                    </div>

                </div>

                <h3 class="mt-2"><a class="object-id-9" href="https://mousephenotypedev.org/human-diseases/">Human
                    Diseases</a></h3>

                <div class="mobile-nav__sub-pages">

                </div>

                <h3 class="mt-2"><a class="object-id-10" href="https://mousephenotypedev.org/news-and-events/">News
                    &#038;
                    Events</a></h3>

                <div class="mobile-nav__sub-pages">
                    <p><a href="https://mousephenotypedev.org/news-and-events/events/">Events</a></p>
                    <div class="sub-pages">
                    </div>
                    <p><a href="https://mousephenotypedev.org/news-and-events/news/">News</a></p>
                    <div class="sub-pages">
                    </div>
                    <p><a href="https://mousephenotypedev.org/news-and-events/latest-publications/">Latest
                        Publications</a></p>
                    <div class="sub-pages">
                        <p><a href="https://mousephenotypedev.org/news-and-events/latest-publications/papers-using-impc-resources/">Papers
                            using IMPC resources</a></p>

                        <p><a href="https://mousephenotypedev.org/news-and-events/latest-publications/impc-papers/">IMPC
                            Papers</a></p>

                        <p><a href="https://mousephenotypedev.org/news-and-events/latest-publications/test-page/">Test
                            Page</a></p>

                    </div>

                </div>

                <h3 class="mt-2"><a class="object-id-105" href="https://mousephenotypedev.org/news-and-events/2018/12/03/blog-2/">Blog</a></h3>

                <div class="mobile-nav__sub-pages">

                </div>

                <h3 class="mt-2"><a class="object-id-11" href="https://mousephenotypedev.org/faqs/">FAQs</a></h3>

                <div class="mobile-nav__sub-pages">

                </div>

                <h3 class="mt-2"><a class="object-id-107" href="https://mousephenotypedev.org/news-and-events/2018/12/03/forum-2/">Forum</a></h3>

                <div class="mobile-nav__sub-pages">

                </div>

                <h3 class="mt-2"><a class="object-id-12" href="https://mousephenotypedev.org/contact-us/">Contact
                    Us</a></h3>

                <div class="mobile-nav__sub-pages">

                </div>
            </div>
        </div>
    </div>

    <div class="about-menu sub-menu collapse" id="about-menu">
        <div class="about-menu__inside">
            <div class="container">

                <a href="https://mousephenotypedev.org/about-impc/">About IMPC</a>


                <a href="https://mousephenotypedev.org/about-impc/collaborations/">Collaborations</a>

                <a href="https://mousephenotypedev.org/about-impc/funding/">Funding</a>

                <a href="https://mousephenotypedev.org/about-impc/consortium-members/">Consortium members</a>

                <a href="https://mousephenotypedev.org/about-impc/animal-welfare/">Animal welfare</a>

                <a href="https://mousephenotypedev.org/about-impc/governance/">Governance</a>

                <a href="https://mousephenotypedev.org/about-impc/our-people/">Our people</a>


            </div>
        </div>
        <div class="about-menu__drop"></div>
    </div>

    <div class="data-menu sub-menu collapse" id="data-menu">
        <div class="data-menu__inside">
            <div class="container">


                <div class="row no-gutters">
                    <div class="col col-auto text-left">
                        <a href="https://mousephenotypedev.org/understanding-the-data/">Understanding the Data</a>
                    </div>
                    <div class="col col-auto text-left">
                        <a href="https://mousephenotypedev.org/understanding-the-data/gene-knockout-technology/">Gene
                            knockout technology</a>
                        <div class="sub-pages">
                        </div>

                    </div>
                    <div class="col col-auto text-left">
                        <a href="https://mousephenotypedev.org/understanding-the-data/research-highlights/">Research
                            highlights</a>
                        <div class="sub-pages">
                            <p><a href="https://mousephenotypedev.org/understanding-the-data/research-highlights/vignettes/">Vignettes</a></p>

                            <p><a href="https://mousephenotypedev.org/understanding-the-data/research-highlights/embryo-development/">Embryo
                                development</a></p>

                            <p><a href="https://mousephenotypedev.org/understanding-the-data/research-highlights/sexual-dimorphism/">Sexual
                                dimorphism</a></p>

                            <p><a href="https://mousephenotypedev.org/understanding-the-data/research-highlights/cardiovascular/">Cardiovascular</a></p>

                            <p><a href="https://mousephenotypedev.org/understanding-the-data/research-highlights/hearing/">Hearing</a></p>

                            <p><a href="https://mousephenotypedev.org/understanding-the-data/research-highlights/metabolism/">Metabolism</a></p>

                            <p><a href="https://mousephenotypedev.org/understanding-the-data/research-highlights/translating-to-other-species/">Translating
                                to other species</a></p>

                            <p><a href="https://mousephenotypedev.org/understanding-the-data/research-highlights/embryo-development-2/">Embryo
                                development</a></p>

                        </div>

                    </div>
                    <div class="col col-auto text-left">
                        <a href="https://mousephenotypedev.org/understanding-the-data/latest-data-release/">Latest
                            data
                            release</a>
                        <div class="sub-pages">
                        </div>

                    </div>
                    <div class="col col-auto text-left">
                        <a href="https://mousephenotypedev.org/understanding-the-data/phenotyping-process-impress/">Phenotyping
                            process (IMPRess)</a>
                        <div class="sub-pages">
                        </div>

                    </div>
                </div>
            </div>
        </div>
        <div class="data-menu__drop"></div>
    </div>

    <div class="news-menu sub-menu collapse" id="news-menu">
        <div class="news-menu__inside">
            <div class="container">


                <div class="row no-gutters justify-content-end">
                    <div class="col col-auto text-left">
                        <a href="https://mousephenotypedev.org/news-and-events/">News &#038; Events</a>
                    </div>

                    <div class="col col-auto text-left">
                        <a href="https://mousephenotypedev.org/news-and-events/events/">Events</a>
                        <div class="sub-pages">
                        </div>

                    </div>
                    <div class="col col-auto text-left">
                        <a href="https://mousephenotypedev.org/news-and-events/news/">News</a>
                        <div class="sub-pages">
                        </div>

                    </div>
                    <div class="col col-auto text-left">
                        <a href="https://mousephenotypedev.org/news-and-events/latest-publications/">Latest
                            Publications</a>
                        <div class="sub-pages">
                            <p><a href="https://mousephenotypedev.org/news-and-events/latest-publications/papers-using-impc-resources/">Papers
                                using IMPC resources</a></p>

                            <p><a href="https://mousephenotypedev.org/news-and-events/latest-publications/impc-papers/">IMPC
                                Papers</a></p>

                            <p><a href="https://mousephenotypedev.org/news-and-events/latest-publications/test-page/">Test
                                Page</a></p>

                        </div>

                    </div>
                </div>
            </div>
        </div>
        <div class="news-menu__drop"></div>
    </div>

</div>



<div class="click-guard"></div>

<main id="main" class="main" role="main">
    <div class="container-fluid">
        <div class="single-header ">
            <img src="${baseUrl}/img/defaultBanner.png" />
            <div class="row text-center">
                <div class="col-12 col-md-6 offset-md-3">
                    <div class="portal-search pb-5 mb-5 mt-5">
                        <div class="portal-search__tabs">
                            <a data-type="gene" class="active portalTab portalTabSearchPage left-shadow" href="#">Genes</a>
                            <a data-type="pheno" class="portalTab portalTabSearchPage right-shadow" href="#">Phenotypes</a>
                        </div>
                        <div class="portal-search__inputs">
                            <form action="https://mousephenotypedev.org/portal-search/">
                                <input id="searchInput" name="term" class="portal-search__input" placeholder="Search the portal..."
                                       type="text" />
                                <button id="searchIcon" type="submit"> <i class="fas fa-search"></i></button>
                                <input id="searchType" type="hidden" name="type" value="gene"></input>
                                <div id="searchLoader" class="lds-ring">
                                    <div></div>
                                    <div></div>
                                    <div></div>
                                    <div></div>
                                </div>
                            </form>
                        </div>
                    </div>
                </div>
            </div>
        </div>
        <jsp:doBody />
    </div>

    <div class="news-letter pt-5 pb-5">
        <div class="container">
            <div class="row">
                <div class="col-12 col-md-8 offset-md-2 text-center">
                    <h2>The IMPC Newsletter</h2>
                    <p class="mt-4">
                        Get highlights of the most important data releases,
                        news and events, delivered straight to your email inbox</p>
                    <a class="btn btn-mailing btn-primary" target="_blank" href="https://forms.office.com/Pages/ResponsePage.aspx?id=jYTJ3EdDnkKo2NytnQUzMV3vNFn2DMZLqTjqljGsCfNUMDg0Q09OQThJUkVaVUpONVpSTVVSVEZERy4u
">Subscribe
                        to newsletter</a>
                </div>
            </div>
        </div>
    </div>
    </div>
</main>

<div class="footer">
    <div class="container">
        <div class="row">

            <div class="col-12 col-md-6">
                <p><strong>© 2018 IMPC International Mouse Phenotyping Consortium.</strong></p>
                <p><strong>All Rights Reserved.<br />
                    <a href="#">Accesibility &amp; Cookies</a></strong><br />
                    <a href="#"><strong>Terms of use</strong></a></p>
            </div>

            <div class="col-12 col-md-3 footer-nav">
                <div class="menu-main-nav-container">
                    <ul id="menu-main-nav-1" class="menu">
                        <li class="menu-item menu-item-type-post_type menu-item-object-page menu-item-16"><a href="https://mousephenotypedev.org/about-impc/">About
                            IMPC</a></li>
                        <li class="menu-item menu-item-type-post_type menu-item-object-page menu-item-17"><a href="https://mousephenotypedev.org/understanding-the-data/">Understanding
                            the Data</a></li>
                        <li class="menu-item menu-item-type-post_type menu-item-object-page menu-item-18"><a href="https://mousephenotypedev.org/human-diseases/">Human
                            Diseases</a></li>
                        <li class="menu-item menu-item-type-post_type menu-item-object-page menu-item-19"><a href="https://mousephenotypedev.org/news-and-events/">News
                            &#038; Events</a></li>
                        <li class="menu-item menu-item-type-custom menu-item-object-custom menu-item-20"><a href="#">Blog</a></li>
                    </ul>
                </div>
            </div>

            <div class="col-12 col-md-3 footer-nav">
                <div class="menu-top-nav-container">
                    <ul id="menu-top-nav-1" class="menu">
                        <li class="menu-item menu-item-type-post_type menu-item-object-page page_item page-item-11 menu-item-13"><a
                                href="https://mousephenotypedev.org/faqs/">FAQs</a></li>
                        <li class="menu-item menu-item-type-custom menu-item-object-custom menu-item-14"><a href="#">Forum</a></li>
                        <li class="menu-item menu-item-type-post_type menu-item-object-page menu-item-15"><a href="https://mousephenotypedev.org/contact-us/">Contact
                            Us</a></li>
                    </ul>
                </div>
            </div>

        </div>
        <div class="row">
            <div class="col">
                <ul class="footer__social">
                    <li>
                        <a href="https://twitter.com/impc" target="_blank"><i class="fab fa-twitter"></i></a>
                    </li>
                    <li>
                        <a href="https://www.instagram.com/geneoftheday/" target="_blank"><i class="fab fa-instagram"></i></a>
                    </li>
                    <li>
                        <a href="https://www.youtube.com/channel/UCXp3DhDYbpJHu4MCX_wZKww" target="_blank"><i class="fab fa-youtube"></i></a>
                    </li>
                </ul>
            </div>
        </div>
    </div>
    
    <jsp:invoke fragment="addToFooter"/>
</div>
<script type='text/javascript' src='${baseUrl}/js/searchAndFacet/searchAndFacetConfig.js?v=${version}'></script>
<%-- <script type='text/javascript' src='${baseUrl}/js/utils/tools.js?v=${version}'></script> commented out as causing erros - for debug JW--%>
<script type='text/javascript' src='${baseUrl}/js/general/ui.dropdownchecklist_modif.js?v=${version}'></script>
<script type='text/javascript' src='${baseUrl}/js/documentationConfig.js?v=${version}'></script>

</body>

</html>
