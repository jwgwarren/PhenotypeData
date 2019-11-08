<%@taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<%@taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@taglib prefix="t" tagdir="/WEB-INF/tags" %>

<t:genericpage>

    <jsp:attribute name="title">My genes login</jsp:attribute>

    <jsp:attribute name="header">
        <script src='https://www.google.com/recaptcha/api.js'></script>
    </jsp:attribute>

    <jsp:attribute name="bodyTag">
        <body class="no-sidebars small-header">
    </jsp:attribute>

    <jsp:attribute name="addToFooter"></jsp:attribute>

    <jsp:body>

        <div class="container">
            <div class="breadcrumbs" style="box-shadow: none; margin-top: auto; margin: auto; padding: auto">

                <div class="row">
                    <div class="col-md-12">
                        <p><a href="${paBaseUrl}">Home</a>
                            <span class="fal fa-angle-right"></span><a href="${paBaseUrl}/rilogin">My Genes</a>
                            <span class="fal fa-angle-right"></span> Login
                        </p>
                    </div>
                </div>
            </div>

            <div class="row row-over-shadow">
                <div class="col-md-12 white-bg">
                    <div class="page-content">
                        <h2>My Genes login</h2>
                        <p>
                            My Genes login is a place where you can:
                            <ul class="mt-0 pt-0">
                                <li><a href="${paBaseUrl}/newAccountRequest">Create a new account</a></li>
                                <li> <a href="${paBaseUrl}/resetPasswordRequest">Reset your password</a></li>
                                <li>Log in to your My Genes account to view and manage the list of genes you've followed</li>
                            </ul>
                        </p>

                        <div class="login-form row ml-3 mr-3 mb-3">
                            <form action="${paBaseUrl}/rilogin" method="POST" class="form-horizontal">
                                <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
                                <input type="hidden" name="target" value="${target}"/>

                                <c:if test="${param.error != null}">

                                    <c:if test="${empty param.errorMessage}">
                                        <div class="messages error">
                                            <p>Invalid username and password.</p>
                                        </div>
                                    </c:if>
                                    <c:if test="${not empty param.errorMessage}">
                                        <div class="messages error">
                                            <p>${param.errorMessage}</p>
                                        </div>
                                    </c:if>
                                </c:if>
                                <c:if test="${param.logout != null}">
                                    <div class="messages" style="color: indigo">
                                        <p>You have been logged out successfully.</p>
                                    </div>
                                </c:if>
                                <c:if test="${param.deleted != null}">
                                    <div class="messages" style="color: indigo">
                                        <p>Your account has been deleted as requested.</p>
                                    </div>
                                </c:if>
                                <c:if test="${param.status != null}">
                                    <div class="messages" style="color: indigo">
                                        <p>${param.status}</p>
                                    </div>
                                </c:if>

                                <div class="form-item form-type-textfield form-item-name">
                                    <input type="text" class="form-control required" id="username" name="ssoId"
                                           placeholder="Enter e-mail address" required/>
                                </div>

                                <br />

                                <div class="form-item form-type-password form-item-pass">
                                    <input type="password" class="form-control  required" id="password" name="password"
                                           placeholder="Enter password" required/>
                                </div>

                                <div style="padding-top:0.8em;" class="g-recaptcha"
                                     data-sitekey=${recaptchaPublic}></div>

                                <br />

                                <noscript>
                                    <div class="col-12 no-gutters">
                                        <h5 style="float: left">Please enable javascript if you want to log in to follow or stop
                                            following this gene.</h5>
                                    </div>
                                </noscript>

                                <div class="form-actions">
                                    <input type="submit"
                                           class="btn btn-block btn-primary btn-default"
                                           formmethod="POST"
                                           value="Log in"/>
                                </div>
                            </form>
                        </div>
                    </div>
                </div>
            </div>
        </div>

    </jsp:body>
</t:genericpage>