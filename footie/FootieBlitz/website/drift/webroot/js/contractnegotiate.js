
     $(document).ready(function(){

            if ($('#withclause').is(':checked')) {
                $('#releaseclause').show("slow");
            }
            else{
                $('#releaseclause').hide("slow");
            }

            var strD = $('#postdate').val();
            strD = strD.replace("-", "/");
            strD = strD.replace("-", "/");
            var d = new Date(strD);

            $('#expdate').datepicker({ minDate: +37, defaultDate: d, maxDate: +465, onSelect: function(dateText, inst) {  $('#postdate').val($.datepicker.formatDate('yy-mm-dd',$("#expdate").datepicker( 'getDate' ))); }  });

            $('#postdate').val( $.datepicker.formatDate('yy-mm-dd',$("#expdate").datepicker( 'getDate' )) );

           //Vis eller skjul release clause
           $("#withclause").change(function(event){
               $("#btnsign").val("Show offered contract");
               if ($('#withclause').is(':checked')) {
                   $('#releaseclause').show("slow");
               }
               else{
                   $('#releaseclause').hide("slow");
               }
           });

        $("#signonfee").keypress(function (e)
        {
           $("#btnsign").val("Show offered contract");
          //if the letter is not digit then display error and don't type anything
          if( e.which!=8 && e.which!=0 && (e.which<48 || e.which>57))
          {
            //display error message
            $("#signonfeeerror").html("Digits Only").show().fadeOut(3000);
            return false;
          }
        });
        $("#wage").keypress(function (e)
        {
           $("#btnsign").val("Show offered contract");
          //if the letter is not digit then display error and don't type anything
          if( e.which!=8 && e.which!=0 && (e.which<48 || e.which>57))
          {
            //display error message
            $("#wageerror").html("Digits Only").show().fadeOut(3000);
            return false;
          }
        });
        $("#transferfee").keypress(function (e)
        {
           $("#btnsign").val("Show offered contract");
          //if the letter is not digit then display error and don't type anything
          if( e.which!=8 && e.which!=0 && (e.which<48 || e.which>57))
          {
            //display error message
            $("#transferfeeerror").html("Digits Only").show().fadeOut(3000);
            return false;
          }
        });
        $("#assistbonus").keypress(function (e)
        {
           $("#btnsign").val("Show offered contract");
          //if the letter is not digit then display error and don't type anything
          if( e.which!=8 && e.which!=0 && (e.which<48 || e.which>57))
          {
            //display error message
            $("#assisterror").html("Digits Only").show().fadeOut(3000);
            return false;
          }
        });
        $("#goalbonus").keypress(function (e)
        {
           $("#btnsign").val("Show offered contract");
          //if the letter is not digit then display error and don't type anything
          if( e.which!=8 && e.which!=0 && (e.which<48 || e.which>57))
          {
            //display error message
            $("#goalerror").html("Digits Only").show().fadeOut(3000);
            return false;
          }
        });
        $("#releaseclause").keypress(function (e)
        {
           $("#btnsign").val("Show offered contract");
          //if the letter is not digit then display error and don't type anything
          if( e.which!=8 && e.which!=0 && (e.which<48 || e.which>57))
          {
            //display error message
            $("#releaseerror").html("Digits Only").show().fadeOut(3000);
            return false;
          }
        });

        $("#btnrej").click(function(event) {
                $("#signed").val("-1");
                $("form:first").submit();
        });
        $("#btnsign").click(function(event) {
             if ($("#btnsign").val() == "Sign contract"){
                    $("#signed").val("1");
                    $("form:first").submit();
             }
             else{
                    location.reload();
             }
         });

     });