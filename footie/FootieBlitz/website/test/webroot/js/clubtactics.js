    var startX = 0;
     var startY = 0;
     var selPlayer = -1;

     $(document).ready(function(){
         $(".draggable").draggable({

       start: function(event, ui) {

           var Startpos = $(this).position();
           startX = Startpos.left;
           startY = Startpos.top;
       },

       stop: function(event, ui) {

           var Stoppos = $(this).position();
           var dX = Stoppos.left - startX;
           var dY = Stoppos.top - startY; 
           var movedid = "";
           movedid = $(this).attr('id');
           movedid = movedid.replace("pos", "#p");
           var before = $(movedid).val();
           var bX = parseInt(before.split(",")[0]);
           var bY = parseInt(before.split(",")[1]);
           var newX = bX + dX;
           var newY = parseInt(bY + dY/ 0.7);
           $(movedid).val(""+parseInt(newX)+","+ parseInt(newY));
          }
        });

         $( ".lineupnames" ).selectable({
			click: function(event) {
			  $(this).siblings().removeClass("ui-selected");
			  $(this).addClass("ui-selected");
			},
                       stop: function() {
                                $( ".ui-selected", this ).each(function() {
                                        var index = $( "#lineupnames span" ).index( this );

                                        if (selPlayer == -1){
                                               selPlayer = index;
                                        }
                                        else{
                                               var temp = $( "#lineupnames span" ).get(selPlayer).innerHTML;
                                               $( "#lineupnames span" ).get(selPlayer).innerHTML = $( "#lineupnames span" ).get(index).innerHTML;
                                               $( "#lineupnames span" ).get(index).innerHTML = temp;
                                               var num1 = parseInt($( "#lineupnames span" ).get(selPlayer).id.replace("lpl", ""));
                                               var num2 = parseInt($( "#lineupnames span" ).get(index).id.replace("lpl", ""));
                                               var id1 = "#"+$( "#lineupnames span" ).get(selPlayer).id.replace("lpl", "hllpl");
                                               var id2 = "#"+$( "#lineupnames span" ).get(index).id.replace("lpl", "hllpl");
                                               var tempid = $( id1 ).val();
                                               $( id1 ).val( $(id2).val() );
                                               $( id2 ).val( tempid );
                                               if (num1 < 12) $("#pos" + num1).html($( "#lineupnames span" ).get(selPlayer).innerHTML);
                                               if (num2 < 12) $("#pos" + num2).html(temp);
                                               if (num1 > 18 && $( "#lineupnames span" ).get(selPlayer).innerHTML=="Not Selected") $( "#"+$( "#lineupnames span" ).get(selPlayer).id ).hide();
                                               if (num2 > 18 && $( "#lineupnames span" ).get(index).innerHTML=="Not Selected") $( "#"+$( "#lineupnames span" ).get(index).id ).hide();
                                               selPlayer = -1;
                                               $(this).removeClass("ui-selected");
                                        }
				});

			}

         });

$('.pro').each(function(index, value) { 
   var progress = parseFloat($(this).html());
$(this).css('width', progress + '%' );

 
if(progress >= 95){
     $(this).css('backgroundColor', '#08FF00');
     $(this).css('borderColor', '#08FF48');
}
else if(progress < 95 && progress >= 90){
     $(this).css('backgroundColor', '#22FF00');
     $(this).css('borderColor', '#22FF48');
}
else if(progress < 90 && progress >= 85){
     $(this).css('backgroundColor', '#3DFF00');
     $(this).css('borderColor', '#3DFF48');
}
else if(progress < 85 && progress >= 80){
     $(this).css('backgroundColor', '#55FF00');
     $(this).css('borderColor', '#55FF48');
}
else if(progress < 80 && progress >= 75){
     $(this).css('backgroundColor', '#70FF00');
     $(this).css('borderColor', '#70FF48');
}
else if(progress < 75 && progress >= 70){
     $(this).css('backgroundColor', '#88FF00');
     $(this).css('borderColor', '#88FF48');
}
 else if(progress < 70 && progress >= 65){
     $(this).css('backgroundColor', '#A3FF00');
     $(this).css('borderColor', '#A3FF48');
}
 else if(progress < 65 && progress >= 60){
     $(this).css('backgroundColor', '#BBFF00');
     $(this).css('borderColor', '#BBFF48');
}
 else if(progress < 60 && progress >= 55){
     $(this).css('backgroundColor', '#E8FF00');
     $(this).css('borderColor', '#E8FF48');
}
 else if(progress < 55 && progress >= 50){
     $(this).css('backgroundColor', '#FFFF00');
     $(this).css('borderColor', '#FFFF48');
}
 else if(progress < 50 && progress >= 45){
     $(this).css('backgroundColor', '#FFE500');
     $(this).css('borderColor', '#FFE548');
}
 else if(progress < 45 && progress >= 40){
     $(this).css('backgroundColor', '#FFCC00');
     $(this).css('borderColor', '#FFCC48');
}
 else if(progress < 40 && progress >= 35){
     $(this).css('backgroundColor', '#FFB200');
     $(this).css('borderColor', '#FFB248');
}
 else if(progress < 35 && progress >= 30){
     $(this).css('backgroundColor', '#FF9900');
     $(this).css('borderColor', '#FF9948');
}
 else if(progress < 30 && progress >= 25){
     $(this).css('backgroundColor', '#FF6600');
     $(this).css('borderColor', '#FF6648');
}
 else if(progress < 25 && progress >= 20){
     $(this).css('backgroundColor', '#FF4B00');
     $(this).css('borderColor', '#FF4B48');
}
 else if(progress < 20 && progress >= 15){
     $(this).css('backgroundColor', '#FF3300');
     $(this).css('borderColor', '#FF3348');
}
 else if(progress < 15 && progress > 10){
     $(this).css('backgroundColor', '#FF1800');
     $(this).css('borderColor', '#FF1848');
}
 else if(progress <= 10){
     $(this).css('backgroundColor', '#FF0000');
     $(this).css('borderColor', '#FF0048');
}

});

$("#tblplayers").tablesorter(); 

});