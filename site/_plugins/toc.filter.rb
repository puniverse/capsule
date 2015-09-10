require 'nokogiri'

# http://ruby.bastardsbook.com/chapters/html-parsing/

module TocFilter
  def toc(html)
    output = ""
    ul = Nokogiri::HTML(html).css('#markdown-toc')
    ul.each do |elem| # xpath("//*[@id='markdown-toc']")
      # elem is main toc ul
      elem['id'] = 'docs-sidemenu'
      elem.children.each do |li1|
        # li1 is li level 1
        li1['class'] = 'li1'
        li1.children.select{ |x| x.name == 'ul'}.each do |c|
          # c is ul under li1
          c.children.each do |li2|
            # li2 is li under c
            li2.children.select{ |x| x.name == 'a'}.each do |a|
              # a is "a href" under li level 2
              a['class'] = 'cat-link'
            end
          end
        end
      end
    end
    ul.to_html
  end
end
Liquid::Template.register_filter(TocFilter)