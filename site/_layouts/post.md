<!DOCTYPE html>
<!--[if lt IE 7]>      <html class="no-js lt-ie9 lt-ie8 lt-ie7"> <![endif]-->
<!--[if IE 7]>         <html class="no-js lt-ie9 lt-ie8"> <![endif]-->
<!--[if IE 8]>         <html class="no-js lt-ie9"> <![endif]-->
<!--[if gt IE 8]><!--> <html class="no-js"> <!--<![endif]-->
<html itemscope itemtype="http://schema.org/Organization">
<head>
{% include head.html %}
</head>
<body>

<body class="blog single">

{% include header2.html %}


<article>
    <section id="main">
            <div class="container">
                <div id="post-cont">
                 <h1>{{ page.title }}</h1>
                 {{ content }}
                </div> <!--post-cont-->
            </div><!--container-->
    </section><!--main-->   
</article>

 
<!-- Place this tag right after the last button or just before your close body tag. -->
<script async defer id="github-bjs" src="https://buttons.github.io/buttons.js"></script>

</body>
</html>



