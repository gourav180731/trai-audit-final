/* Gov portal JS — collapsible sidebar, toasts, loading states. No framework. */
(function(){
  // Sidebar toggle (mobile drawer)
  document.addEventListener('DOMContentLoaded', function(){
    var toggle = document.getElementById('govSidebarToggle');
    var sidebar = document.getElementById('govSidebar');
    var backdrop = document.getElementById('govSidebarBackdrop');
    function open(){ sidebar && sidebar.classList.add('is-open'); backdrop && backdrop.classList.add('is-open'); document.body.style.overflow='hidden'; }
    function close(){ sidebar && sidebar.classList.remove('is-open'); backdrop && backdrop.classList.remove('is-open'); document.body.style.overflow=''; }
    if(toggle){ toggle.addEventListener('click', function(e){ e.preventDefault(); sidebar.classList.contains('is-open') ? close() : open(); }); }
    if(backdrop){ backdrop.addEventListener('click', close); }
    // Escape to close
    document.addEventListener('keydown', function(e){ if(e.key==='Escape') close(); });
    // Prevent double submit
    document.querySelectorAll('form[data-gov-prevent-double]').forEach(function(f){
      f.addEventListener('submit', function(){
        var btn=f.querySelector('button[type=submit]');
        if(btn){ btn.disabled=true; var orig=btn.innerHTML; btn.dataset.orig=orig; btn.innerHTML='<span class="gov-spinner" style="display:inline-block;vertical-align:middle;margin-right:6px"></span> Processing…'; setTimeout(function(){ btn.disabled=false; btn.innerHTML=orig; }, 8000); }
      });
    });
  });
})();
