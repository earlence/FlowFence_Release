class SmartPlug 
    attr_accessor :id, :state
    def initialize(id, state)
        @id = id
        @state = state
    end    

    def to_s
        "Smart Plug: #{id} State = #{state}"
    end
end