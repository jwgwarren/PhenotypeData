$(document).ready(function () {

	var exampleSearch =
		'<h4><a id="sdoc" href="' + baseUrl + '/documentation/doc-search">Search page documentation</a></h4><br>'
		+ '<h3 id="samplesrch" id="briefDocCap">Example Searches</h3>'
		+ '<p>Sample queries for several fields are shown. Click the desired query to execute one of the samples.'
		+ '</p>'
		+ '<h5>Gene query examples</h5>'
		+ '<p>'
		+ '<a href="' + baseUrl + '/search/gene?kw=akt2&fq=*:*">Akt2</a>'
		+ '- looking for a specific gene, Akt2'
		+ '<br>'
		+ '<a href="' + baseUrl + '/search/gene?kw=*rik&fq=*:*">*rik</a>'
		+ '- looking for all Riken genes'
		+ '<br>'
		+ '<a href="' + baseUrl + '/search/gene?kw=hox*&fq=*:*">hox*</a>'
		+ '- looking for all hox genes'
		+ '</p>'
		+ '<h5>Phenotype query examples</h5>'
		+ '<p>'
		+ '<a href="' + baseUrl + '/search/mp?kw=abnormal skin morphology&fq=top_level_mp_term:*">abnormal skin morphology</a>'
		+ '- looking for a specific phenotype'
		+ '<br>'
		+ '<a href="' + baseUrl + '/search/mp?kw=ear&fq=top_level_mp_term:*">ear</a>'
		+ '- find all ear related phenotypes'
		+ '</p>'
		+ '<h5>Phrase query Example</h5>'
		+ '<p>'
		+ '<a href="' + baseUrl + '/search/gene?kw=zinc finger protein&fq=*:*">zinc finger protein</a>'
		+ '- looking for genes whose product is zinc finger protein'
		+ '</p>'
		+ '<h5>Phrase wildcard query Example</h5>'
		+ '<p>'
		+ '<a href="' + baseUrl + '/search/mp?kw=abnormal phy*&fq=top_level_mp_term:*">abnormal phy*</a>'
		+ '- can look for phenotypes that contain abnormal phenotype or abnormal physiology.<br>'
		+ 'Supported queries are a mixture of word with *, eg. abn* immune phy*.'
		+ '</p>';

	//$('a#searchExample').mouseover(function(){
	$('i.searchExample').mouseover(function(){
		// override default behavior from default.js - Nicolas
		return false;
	})
	// initialze search example qTip with close button and proper positioning
	//$("a#searchExample").qtip({
	$("i.searchExample").qtip({
		hide: true,
		content: {
			text: exampleSearch,
			title: {'button': 'close'}
		},
		style: {
			classes: 'qtipimpc',
			tip: {corner: 'top center'}
		},
		position: {my: 'left top',
			adjust: {x: -480, y: 0}
		},
		show: {
			event: 'click' //override the default mouseover
		}
	});

	/* $("i.batchQuery").qtip({
	 content: "Click to go to batch query page",
	 style: {
	 classes: 'qtipimpc',
	 tip: {corner: 'top center'}
	 },
	 position: {my: 'left top',
	 adjust: {x: -125 , y: 0}
	 }
	 }); */

	$("span.direct").qtip({
		content: "Matches GWAS traits - Phenotypes for this knockout strain are highly similar to human GWAS traits "
		+ "associated with SNPs located in, or proximal to, orthologous genes. Mappings done by manual curation." ,
		style: {
			classes: 'qtipimpc',
			tip: {corner: 'top center'}
		},
		position: {my: 'left top',
			adjust: {x: -280, y: 0}
		}
	});

	$("span.indirect").qtip({
		content: "Similar to GWAS traits - Phenotypes for this knockout strain have some overlap to human GWAS traits "
		+ "associated with SNPs  located in, or proximal to, orthologous genes. Mappings done by manual curation.",
		style: {
			classes: 'qtipimpc',
			tip: {corner: 'top center'}
		},
		position: {my: 'left top',
			adjust: {x: -280, y: 0}
		}
	});

	var matchedFacet = false;
	var facet2Fq = {
		'gene' : '*:*',
		'mp'   : 'top_level_mp_term:*',
		'disease' : '*:*',
		'anatomy' : 'selected_top_level_anatomy_term:*',
		//'pipeline' : 'pipeline_stable_id:*',
		'images' : '*:*',
		'allele2' : 'type:Allele'
	};
	var facet2Label = {
		'gene'        : 'Genes',
		'mp'          : 'Phenotypes',
		'disease'     : 'Diseases',
		'anatomy'     : 'Anatomy',
		'impc_images' : 'Images',
		'allele2'     : 'Products'
	};

	// generic search input autocomplete javascript
	var solrBq = "&bq=marker_symbol:*^100 hp_term:*^95 hp_term_synonym:*^95 mp_term:*^90 mp_term_synonym:*^80 mp_narrow_synonym:*^75 disease_term:*^70 anatomy_term:*^60 anatomy_term_synonym:*^50";

	var srchkw = $.fn.fetchUrlParams('kw') == undefined ? "Search" : $.fn.fetchUrlParams('kw').replace("\\%3A",":").replace("\\-", "-");
    srchkw = srchkw.replace(/%22/g, '');
    if (srchkw == '*') {
        srchkw = srchkw.replace('*', '');
    }
	$("input#s").val(decodeURI(srchkw));

	$("input#s").click(function(){
		if ( $(this).val() == 'Search' ) {
			$(this).val('');
		};
	});
	$('#clearIcon').click(function(){
		$("input#s").val('');
	});

    var qRe = new RegExp(" in (Genes|Phenotypes|Diseases|Anatomy|Images|Products)$");

	$( "input#s" ).autocomplete({
		source: function( request, response ) {
			var qfStr = request.term.indexOf("*") != -1 ? "auto_suggest" : "string auto_suggest";
			var facetStr = "&facet=on&facet.field=docType&facet.mincount=1&facet.limit=-1";
			var sortStr = "&sort=score desc";

                $.ajax({
				//url: solrUrl + "/autosuggest/select?wt=json&qf=string auto_suggest&defType=edismax" + solrBq,
				url: solrUrl + "/autosuggest/select?rows=5&fq=!docType:gwas AND !docType:hp&wt=json&qf=" + qfStr + "&defType=edismax" + solrBq + facetStr + sortStr,
				dataType: "jsonp",
				'jsonp': 'json.wrf',
				data: {
					//q: '"'+request.term+'"'
					q: request.term
				},
				success: function( data ) {

					var docs = data.response.docs;

					var suggests = [];
					var seenTerm = {};
					for ( var i=0; i<docs.length; i++ ){
						var facet = null;

						for ( var key in docs[i] ){

							if ( facet == 'hp' && (key == 'hpmp_id' || key == 'hpmp_term') ){
								continue;
							}

							if ( key == 'docType' ){
								facet = docs[i][key].toString();
							}
							else {

								var term = docs[i][key].toString();
								var termHl = term;

								// highlight multiple matches
								// (partial matches) while users
								// typing in search keyword(s)
								// let jquery autocomplet UI handles
								// the wildcard
								// var termStr =
								// $('input#s').val().trim('
								// ').split('
								// ').join('|').replace(/\*|"|'/g,
								// '');
								var termStr = $('input#s').val().trim(' ').split(' ').join('|').replace(/\*|"|'/g, '').replace(/\(/g,'\\(').replace(/\)/g,'\\)');

								var re = new RegExp("(" + termStr + ")", "gi") ;
								var termHl = termHl.replace(re,"<b class='sugTerm'>$1</b>");

								// add only once with the top score
								var lowerCaseTerm = term.toLowerCase();
								if ( seenTerm[lowerCaseTerm] == undefined){
									seenTerm[lowerCaseTerm]++;
									suggests.push("<span class='" + facet + " sugList'>" + termHl + "</span>");
								}

							}
						}
					}
					var dataTypeVal = [];

					for( var corename in facet2Label ) {
						dataTypeVal.push(_getDropdownList(corename, facet2Label, request.term));
					}

                    if ( suggests.length > 0 ) {
                        dataTypeVal.push("<hr>");
                        for (var i = 0; i < suggests.length; i++) {
                            dataTypeVal.push(suggests[i]);
                        }
                    }

					response(dataTypeVal);
				}
			});
		},
		focus: function (event, ui) {
			var thisInput = $(ui.item.label).text().replace(/<\/?span>|^\w* : /g,'');
			//alert("input box: " + thisInput);
			//this.value = '"' + thisInput.trim() + '"';  // double quote value when mouseover or KB UP.DOWN a dropdown list
            // assign value to input box
            this.value = thisInput.trim();

			event.preventDefault(); // Prevent the default focus behavior.
		},
		minLength: 3,
		select: function( event, ui ) {
			// select by mouse / KB
			//console.log(this.value + ' vs ' + ui.item.label);

			//var oriText = $(ui.item.label).text();

			var facet = $(ui.item.label).attr('class').replace(' sugList', '') == 'hp' ? 'mp' : $(ui.item.label).attr('class').replace(' sugList', '');

			var q;
			//var matched = this.value.match(/.+ » (MP:\d+) - .+/);

			// var matched = decodeURIComponent(this.value).match(/.+(MP:\d+) - .+/);
			// //var matched = this.value.match(/.+(MP:\d+) - .+/);
			// if ( matched ){
			// 	q = matched[1];
			// }
			// else {
				var qVal = this.value;
				q = qVal.replace(qRe, "");

			//}
			q = encodeURIComponent(q).replace("%3A", "\\%3A");

            // default to send query to Solr in quotes !!!
            //alert("q : "+ q);
			if (q.indexOf("*") != -1 && q.indexOf(" ") == -1) {
				q = q;
			}
			else {
				q = '"' + q + '"'; // single quotes do NOT work in SOLR query
			}

			//var href = baseUrl + '/search/' + facet  + '?' + "kw=\"" + q + "\"";
            var href = baseUrl + '/search/' + facet  + '?' + 'kw=' + q;
			if (q.match(/(MGI:|MP:|MA:|EMAP:|EMAPA:|HP:|OMIM:|ORPHANET:|DECIPHER:)\d+/i)) {
				href += "&fq=" + facet2Fq[facet];
			}

			document.location.href = href;
			// prevents escaped html tag displayed in input box
			event.preventDefault(); return false;

		},
		open: function(event, ui) {
			// fix jQuery UIs autocomplete width
			$(this).autocomplete("widget").css({
				"width": ($(this).width() + "px")
			});

			$( this ).removeClass( "ui-corner-all" ).addClass( "ui-corner-top" );
		},
		close: function() {
			$( this ).removeClass( "ui-corner-top" ).addClass( "ui-corner-all" );
		}
	}).data("ui-autocomplete")._renderItem = function( ul, item) {
		// prevents HTML tags being escaped

		return $( "<li></li>" )
			.data( "item.autocomplete", item )
			.append( $( "<a></a>" ).html( item.label ) )
			.appendTo( ul );
	};



    // search via ENTER
    $('input#s').keyup(function (e) {
        if (e.keyCode == 13) { // user hits enter
            $(".ui-menu-item").hide();
            //$('ul#ul-id-1').remove();

            //alert('enter: '+ MPI2.searchAndFacetConfig.matchedFacet)
            var input = $('input#s').val().trim();

            //alert("check1 - " +  input + ' ' + solrUrl)
            input = /^\*\**?\*??$/.test(input) ? '' : input;  // lazy matching

            var re = new RegExp("^'(.*)'$");
            input = input.replace(re, "\"$1\""); // only use double quotes for phrase query

            // NOTE: solr special characters to escape
            // + - && || ! ( ) { } [ ] ^ " ~ * ? : \

            input = encodeURIComponent(input);

            input = input.replace("%5B", "\\[");
            input = input.replace("%5D", "\\]");
            input = input.replace("%7B", "\\{");
            input = input.replace("%7D", "\\}");
            input = input.replace("%7C", "\\|");
            input = input.replace("%5C", "\\\\");
            input = input.replace("%3C", "\\<");
            input = input.replace("%3E", "\\>");
            input = input.replace(".", "\\.");
            input = input.replace("(", "\\(");
            input = input.replace(")", "\\)");
            input = input.replace("%2F", "\\/");
            input = input.replace("%60", "\\`");
            input = input.replace("~", "\\~");
            input = input.replace("%", "\\%");
            input = input.replace("!", "\\!");
            input = input.replace("%21", "\\!");
            input = input.replace("-", "\\-");

            if (/^\\%22.+%22$/.test(input)) {
                input = input.replace(/\\/g, ''); //remove starting \ before double quotes
            }

            // no need to escape space - looks cleaner to the users
            // and it is not essential to escape space
            input = input.replace(/\\?%20/g, ' ').replace(qRe, "");

            // var qStr = '"'+request.term+'"'
			//alert("check 2 - input : "+ input);

			// default to search by double quotes in SOLR
            if (input != '') {
				if (input.indexOf("*") != -1 && input.indexOf(" ") == -1) {
					input = input;
				}
				else {
					input = '"' + input + '"';  // single quotes do NOT work in SOLR query
				}
        	}


            // check for current datatype (tab) and use this as default core
            // instead of figuring this out for the user
            var facet = null;

            if ($('ul.tabLabel').size() > 0) {
                // is on search page
                $('ul.tabLabel li').each(function () {
                    if ($(this).hasClass('currDataType')) {
                        facet = $(this).attr('id').replace("T", "");
                    }
                });

                if (input == '') {
                    document.location.href = baseUrl + '/search/' + facet + '?kw=*'; // default
                }
                else if (input.match(/HP\\\%3A\d+/i)) {
                    // work out the mapped mp_id and fire off the query
					facet = "mp";
                    _convertHp2MpAndSearch(input, facet);
                }
                else if (input.match(/MP%3A\d+ - (.+)/i)) {
                    // hover over hp mp mapping but not selecting
                    // eg. Cholesteatoma %C2%BB MP%3A0002102 - abnormal ear morpholog
                    var matched = input.match(/MP%3A\d+ - (.+)/i);
                    var mpTerm = '"' + matched[1] + '"';
                    var fqStr = $.fn.getCurrentFq('mp');

                    document.location.href = baseUrl + '/search/' + facet + '?kw=' + mpTerm + '&fq=' + fqStr;
                }
                // else if (input.match(/^chr)){
                //
                // }
                else {
                    if ( window.location.search == ""){
                        // when url lookes like .../search at end
                        // need to figure out the default datatype tab
                        $.ajax({
                            url: baseUrl + '/fetchDefaultCore?q=' + input,
                            type: 'get',
                            success: function (defaultCore) {
                                //document.location.href = baseUrl + '/search/' + defaultCore + '?kw="' + input + '"';
                                document.location.href = baseUrl + '/search/' + defaultCore + '?kw=' + input;  // do not append double quotes, take whatever the user types
                            }
                        });
                    }
                    else {
                        var fqStr = $.fn.fetchUrlParams("fq");

						if (fqStr != undefined) {
							//document.location.href = baseUrl + '/search/' + facet + '?kw="' + input + '"&fq=' + fqStr;
							document.location.href = baseUrl + '/search/' + facet + '?kw=' + input + '&fq=' + fqStr;
						}
						else {
						   // document.location.href = baseUrl + '/search/' + facet + '?kw="' + input + '"';
							document.location.href = baseUrl + '/search/' + facet + '?kw=' + input;
						}

                    }
                }

            }
            else {

                // is on non-search page
                // user typed something and hit ENTER: need to figure out default core to load on search page
                if ( input == ""){
                    document.location.href = baseUrl + '/search/gene' + '?kw=*';
                }
                else {
                    $.ajax({
                        url: baseUrl + '/fetchDefaultCore?q="' + input + '"',
                        type: 'get',
                        success: function (defaultCore) {
                            document.location.href = baseUrl + '/search/' + defaultCore + '?kw=' + input;
                        }
                    });
                }
            }

        }
    });

    function _getDropdownList(corename, facet2Label, input) {
        var catLabel = "<span class='category'>" + facet2Label[corename] + "</span>";
        return "<span class='" + corename + " sugList'>" + input + " in " + catLabel + "</span>"; // so that we know it is category search
    }

    function _convertHp2MpAndSearch(input, facet){
        input = input.toUpperCase();
        $.ajax({
            url: solrUrl + "/autosuggest/select?fq=docType:hp&wt=json&fl=hpmp_id&rows=1&q=hp_id:"+input,
            dataType: "jsonp",
            jsonp: 'json.wrf',
            type: 'post',
            async: false,
            success: function( json ) {
                var mpid = json.response.docs[0].hpmp_id;
                document.location.href = baseUrl + '/search/' + facet + '?kw="' + mpid + '"&fq=top_level_mp_term:*';
            }
        });
    }

    function _convertInputForSearch(input){
        $.ajax({
            url: solrUrl + "/autosuggest/select?wt=json&rows=1&qf=auto_suggest&defType=edismax&q=\""+input+"\"",
            dataType: "jsonp",
            jsonp: 'json.wrf',
            type: 'post',
            async: false,
            success: function( json ) {
                var doc = json.response.docs[0];
                var facet, q;

                for( var field in doc ) {
                    if ( field != 'docType' ){
                        q = doc[field];
                    }
                    else {
                        facet = doc[field];
                    }
                }

                document.location.href = baseUrl + '/search/' + facet + '?kw=' + q;
            }
        });
    }

});


 	